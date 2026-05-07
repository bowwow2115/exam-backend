import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * data/*.retranslated.json 의 문항/선지 텍스트를 시험 제목으로 찾은 시험에 반영합니다.
 * 사용법: java UpdateExamTranslatedTexts &lt;examTitle&gt; &lt;promptsJson&gt; &lt;choicesJson&gt;
 */
public class UpdateExamTranslatedTexts {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^:}]+):([^}]*)}");
    private static final Pattern PROMPT_ENTRY = Pattern.compile(
            "\\{\\s*\"sortOrder\"\\s*:\\s*(\\d+)\\s*,\\s*\"prompt\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"\\s*\\}"
    );
    private static final Pattern CHOICE_ENTRY = Pattern.compile(
            "\\{\\s*\"questionSortOrder\"\\s*:\\s*(\\d+)\\s*,\\s*\"choiceSortOrder\"\\s*:\\s*(\\d+)\\s*,\\s*\"text\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"\\s*\\}"
    );

    record ChoiceRow(int questionSortOrder, int choiceSortOrder, String text) {}
    record ChoiceState(int questionSortOrder, int choiceSortOrder, String text, boolean correct) {}

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            System.err.println("Usage: java UpdateExamTranslatedTexts <examTitle> <promptsJsonPath> <choicesJsonPath>");
            System.exit(2);
        }
        String examTitle = args[0];
        Path promptsPath = Path.of(args[1]);
        Path choicesPath = Path.of(args[2]);

        Properties properties = loadProperties();
        String url = resolve(properties.getProperty("spring.datasource.url"));
        String username = resolve(properties.getProperty("spring.datasource.username"));
        String password = resolve(properties.getProperty("spring.datasource.password"));

        Map<Integer, String> prompts = parsePrompts(promptsPath);
        List<ChoiceRow> choiceRows = parseChoices(choicesPath);
        if (prompts.isEmpty()) {
            throw new IllegalStateException("No prompts parsed from " + promptsPath);
        }
        if (choiceRows.isEmpty()) {
            throw new IllegalStateException("No choices parsed from " + choicesPath);
        }

        try (Connection connection = DriverManager.getConnection(url, username, password)) {
            connection.setAutoCommit(false);
            long examId = findExamId(connection, examTitle);
            int questionCount = countQuestions(connection, examId);
            int choiceCount = countChoices(connection, examId);
            if (prompts.size() != questionCount) {
                throw new IllegalStateException(
                        "Prompt count " + prompts.size() + " != DB question count " + questionCount + " for exam " + examTitle
                );
            }
            if (choiceRows.size() != choiceCount) {
                throw new IllegalStateException(
                        "Choice row count " + choiceRows.size() + " != DB choice count " + choiceCount + " for exam " + examTitle
                );
            }

            int updatedPrompts = 0;
            try (PreparedStatement update = connection.prepareStatement(
                    "update questions set prompt = ? where exam_id = ? and sort_order = ?"
            )) {
                for (Map.Entry<Integer, String> entry : prompts.entrySet()) {
                    update.setString(1, entry.getValue());
                    update.setLong(2, examId);
                    update.setInt(3, entry.getKey());
                    updatedPrompts += update.executeUpdate();
                }
            }

            int updatedChoices = 0;
            try (PreparedStatement statement = connection.prepareStatement(
                    "update question_choices c set choice_text = ? from questions q "
                            + "where c.question_id = q.id and q.exam_id = ? and q.sort_order = ? and c.sort_order = ?"
            )) {
                for (ChoiceRow row : choiceRows) {
                    statement.setString(1, row.text());
                    statement.setLong(2, examId);
                    statement.setInt(3, row.questionSortOrder());
                    statement.setInt(4, row.choiceSortOrder());
                    updatedChoices += statement.executeUpdate();
                }
            }

            List<ChoiceState> choiceStates = loadChoiceStates(connection, examId);
            Map<Integer, List<ChoiceState>> choicesByQuestion = new TreeMap<>();
            for (ChoiceState state : choiceStates) {
                choicesByQuestion.computeIfAbsent(state.questionSortOrder(), ignored -> new ArrayList<>()).add(state);
            }

            int updatedExplanations = 0;
            try (PreparedStatement update = connection.prepareStatement(
                    "update questions set explanation = ? where exam_id = ? and sort_order = ?"
            )) {
                for (Map.Entry<Integer, String> entry : prompts.entrySet()) {
                    List<ChoiceState> states = choicesByQuestion.getOrDefault(entry.getKey(), List.of());
                    update.setString(1, buildQuestionExplanation(entry.getValue(), states));
                    update.setLong(2, examId);
                    update.setInt(3, entry.getKey());
                    updatedExplanations += update.executeUpdate();
                }
            }

            int updatedRationales = 0;
            try (PreparedStatement update = connection.prepareStatement(
                    "update question_choices c set rationale = ? from questions q "
                            + "where c.question_id = q.id and q.exam_id = ? and q.sort_order = ? and c.sort_order = ?"
            )) {
                for (ChoiceState state : choiceStates) {
                    List<ChoiceState> states = choicesByQuestion.getOrDefault(state.questionSortOrder(), List.of());
                    update.setString(1, buildChoiceRationale(state, states));
                    update.setLong(2, examId);
                    update.setInt(3, state.questionSortOrder());
                    update.setInt(4, state.choiceSortOrder());
                    updatedRationales += update.executeUpdate();
                }
            }

            connection.commit();
            System.out.println("examTitle=" + examTitle);
            System.out.println("examId=" + examId);
            System.out.println("updatedPrompts=" + updatedPrompts);
            System.out.println("updatedChoices=" + updatedChoices);
            System.out.println("updatedExplanations=" + updatedExplanations);
            System.out.println("updatedRationales=" + updatedRationales);
        }
    }

    private static Properties loadProperties() throws IOException {
        Properties properties = new Properties();
        try (var input = Files.newInputStream(Path.of("src/main/resources/application.properties"))) {
            properties.load(input);
        }
        return properties;
    }

    private static String resolve(String value) {
        Matcher matcher = PLACEHOLDER.matcher(value);
        if (!matcher.matches()) {
            return value;
        }
        return System.getenv().getOrDefault(matcher.group(1), matcher.group(2));
    }

    private static long findExamId(Connection connection, String title) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "select id from exams where title = ? order by id desc limit 1"
        )) {
            statement.setString(1, title);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalStateException("Exam not found: " + title);
                }
                return rs.getLong(1);
            }
        }
    }

    private static int countQuestions(Connection connection, long examId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "select count(*) from questions where exam_id = ?"
        )) {
            statement.setLong(1, examId);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private static int countChoices(Connection connection, long examId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "select count(*) from question_choices c join questions q on c.question_id = q.id where q.exam_id = ?"
        )) {
            statement.setLong(1, examId);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private static List<ChoiceState> loadChoiceStates(Connection connection, long examId) throws Exception {
        List<ChoiceState> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "select q.sort_order as question_sort_order, c.sort_order as choice_sort_order, "
                        + "c.choice_text, c.is_correct "
                        + "from question_choices c join questions q on c.question_id = q.id "
                        + "where q.exam_id = ? order by q.sort_order, c.sort_order"
        )) {
            statement.setLong(1, examId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    rows.add(new ChoiceState(
                            rs.getInt("question_sort_order"),
                            rs.getInt("choice_sort_order"),
                            rs.getString("choice_text"),
                            rs.getBoolean("is_correct")
                    ));
                }
            }
        }
        return rows;
    }

    private static String buildQuestionExplanation(String prompt, List<ChoiceState> choices) {
        List<ChoiceState> correctChoices = choices.stream()
                .filter(ChoiceState::correct)
                .toList();
        String correctLabels = correctLabels(correctChoices);
        StringBuilder builder = new StringBuilder();
        builder.append("[해설]\n");
        builder.append("정답은 ").append(correctLabels).append("입니다.\n\n");
        builder.append("[문항]\n").append(prompt).append("\n\n");
        builder.append("[정답 보기]\n");
        for (ChoiceState choice : correctChoices) {
            builder.append("- ").append(choice.choiceSortOrder()).append(". ").append(choice.text()).append("\n");
        }
        builder.append("\n위 정답 보기가 문제에서 제시한 요구사항과 제약 조건에 가장 부합합니다.");
        return clip(builder.toString(), 12_000);
    }

    private static String buildChoiceRationale(ChoiceState choice, List<ChoiceState> choices) {
        List<ChoiceState> correctChoices = choices.stream()
                .filter(ChoiceState::correct)
                .toList();
        if (choice.correct()) {
            return clip(
                    choice.choiceSortOrder() + "번 보기는 정답입니다. "
                            + "문제의 요구사항을 충족하는 선택지입니다.\n\n[선택지]\n" + choice.text(),
                    7_900
            );
        }
        return clip(
                choice.choiceSortOrder() + "번 보기는 오답입니다. "
                        + "정답은 " + correctLabels(correctChoices) + "입니다. "
                        + "이 선택지는 문제의 요구사항을 가장 잘 충족하지 않습니다.\n\n[선택지]\n" + choice.text(),
                7_900
        );
    }

    private static String correctLabels(List<ChoiceState> correctChoices) {
        if (correctChoices.isEmpty()) {
            return "없음";
        }
        return correctChoices.stream()
                .map(choice -> choice.choiceSortOrder() + "번")
                .reduce((left, right) -> left + ", " + right)
                .orElse("없음");
    }

    private static String clip(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength - 1) + "…";
    }

    private static Map<Integer, String> parsePrompts(Path path) throws IOException {
        String content = Files.readString(path);
        Map<Integer, String> prompts = new HashMap<>();
        Matcher matcher = PROMPT_ENTRY.matcher(content);
        while (matcher.find()) {
            int sortOrder = Integer.parseInt(matcher.group(1));
            prompts.put(sortOrder, unescapeJson(matcher.group(2)));
        }
        return prompts;
    }

    private static List<ChoiceRow> parseChoices(Path path) throws IOException {
        String content = Files.readString(path);
        List<ChoiceRow> rows = new ArrayList<>();
        Matcher matcher = CHOICE_ENTRY.matcher(content);
        while (matcher.find()) {
            int q = Integer.parseInt(matcher.group(1));
            int c = Integer.parseInt(matcher.group(2));
            rows.add(new ChoiceRow(q, c, unescapeJson(matcher.group(3))));
        }
        return rows;
    }

    private static String unescapeJson(String escaped) {
        String value = escaped;
        value = value.replace("\\\"", "\"");
        value = value.replace("\\\\", "\\");
        value = value.replace("\\n", "\n");
        value = value.replace("\\r", "\r");
        value = value.replace("\\t", "\t");
        return value;
    }
}
