import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UpdateQuestionPrompts {

    private static final String EXAM_TITLE = "AWS Developer Associate Practice Exam - Korean";
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^:}]+):([^}]*)}");
    private static final Pattern ENTRY = Pattern.compile(
            "\\{\\s*\"sortOrder\"\\s*:\\s*(\\d+)\\s*,\\s*\"prompt\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"\\s*\\}"
    );

    public static void main(String[] args) throws Exception {
        Properties properties = loadProperties();
        String url = resolve(properties.getProperty("spring.datasource.url"));
        String username = resolve(properties.getProperty("spring.datasource.username"));
        String password = resolve(properties.getProperty("spring.datasource.password"));
        Map<Integer, String> prompts = parsePrompts(Path.of("data/prompts.retranslated.json"));
        if (prompts.size() != 387) {
            throw new IllegalStateException("Expected 387 prompts but got " + prompts.size());
        }

        try (Connection connection = DriverManager.getConnection(url, username, password)) {
            connection.setAutoCommit(false);
            long examId = findExamId(connection);
            int questionCount = countQuestions(connection, examId);
            if (questionCount != 387) {
                throw new IllegalStateException("Expected 387 questions in DB but got " + questionCount);
            }

            int updated = 0;
            try (PreparedStatement update = connection.prepareStatement(
                    "update questions set prompt = ? where exam_id = ? and sort_order = ?"
            )) {
                for (Map.Entry<Integer, String> entry : prompts.entrySet()) {
                    update.setString(1, entry.getValue());
                    update.setLong(2, examId);
                    update.setInt(3, entry.getKey());
                    updated += update.executeUpdate();
                }
            }
            connection.commit();
            System.out.println("examId=" + examId);
            System.out.println("updatedRows=" + updated);
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

    private static long findExamId(Connection connection) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "select id from exams where title = ? order by id desc limit 1"
        )) {
            statement.setString(1, EXAM_TITLE);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalStateException("Exam not found: " + EXAM_TITLE);
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

    private static Map<Integer, String> parsePrompts(Path path) throws IOException {
        String content = Files.readString(path);
        Map<Integer, String> prompts = new HashMap<>();
        Matcher matcher = ENTRY.matcher(content);
        while (matcher.find()) {
            int sortOrder = Integer.parseInt(matcher.group(1));
            String prompt = unescapeJson(matcher.group(2));
            prompts.put(sortOrder, prompt);
        }
        return prompts;
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
