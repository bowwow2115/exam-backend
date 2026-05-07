import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UpdateExamExplanationsFromImport {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^:}]+):([^}]*)}");

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("Usage: java UpdateExamExplanationsFromImport <import-json> [<import-json>...]");
            System.exit(2);
        }

        Properties properties = new Properties();
        try (var in = Files.newInputStream(Path.of("src/main/resources/application.properties"))) {
            properties.load(in);
        }

        String url = resolve(properties.getProperty("spring.datasource.url"));
        String user = resolve(properties.getProperty("spring.datasource.username"));
        String pass = resolve(properties.getProperty("spring.datasource.password"));
        ObjectMapper objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        try (Connection con = DriverManager.getConnection(url, user, pass)) {
            con.setAutoCommit(false);
            try {
                for (String arg : args) {
                    updateOne(con, objectMapper, Path.of(arg));
                }
                con.commit();
            } catch (Exception e) {
                con.rollback();
                throw e;
            }
        }
    }

    private static void updateOne(Connection con, ObjectMapper objectMapper, Path path) throws Exception {
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("Import JSON not found: " + path);
        }

        ExamImport exam = objectMapper.readValue(path.toFile(), ExamImport.class);
        if (exam.title == null || exam.questions == null || exam.questions.isEmpty()) {
            throw new IllegalArgumentException("Invalid import JSON: " + path);
        }

        long examId = findExamId(con, exam.title);
        int updatedQuestions = 0;
        int updatedChoices = 0;

        try (PreparedStatement updateQuestion = con.prepareStatement(
                "update questions set explanation = ? where exam_id = ? and sort_order = ?"
        );
             PreparedStatement updateChoice = con.prepareStatement(
                     "update question_choices c set rationale = ? from questions q "
                             + "where c.question_id = q.id and q.exam_id = ? and q.sort_order = ? and c.sort_order = ?"
             )) {
            for (int qi = 0; qi < exam.questions.size(); qi++) {
                QuestionImport question = exam.questions.get(qi);
                int questionSortOrder = qi + 1;

                updateQuestion.setString(1, blankToNull(question.explanation));
                updateQuestion.setLong(2, examId);
                updateQuestion.setInt(3, questionSortOrder);
                updatedQuestions += updateQuestion.executeUpdate();

                if (question.choices == null) {
                    continue;
                }
                for (int ci = 0; ci < question.choices.size(); ci++) {
                    ChoiceImport choice = question.choices.get(ci);
                    updateChoice.setString(1, blankToNull(choice.rationale));
                    updateChoice.setLong(2, examId);
                    updateChoice.setInt(3, questionSortOrder);
                    updateChoice.setInt(4, ci + 1);
                    updatedChoices += updateChoice.executeUpdate();
                }
            }
        }

        System.out.printf(
                "title=%s examId=%d updatedQuestions=%d updatedChoices=%d%n",
                exam.title,
                examId,
                updatedQuestions,
                updatedChoices
        );
    }

    private static long findExamId(Connection con, String title) throws Exception {
        try (PreparedStatement ps = con.prepareStatement(
                "select id from exams where title = ? order by id desc limit 1"
        )) {
            ps.setString(1, title);
            try (var rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalStateException("Exam not found: " + title);
                }
                return rs.getLong(1);
            }
        }
    }

    private static String blankToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value;
    }

    private static String resolve(String value) {
        Matcher matcher = PLACEHOLDER.matcher(value);
        if (!matcher.matches()) {
            return value;
        }
        return System.getenv().getOrDefault(matcher.group(1), matcher.group(2));
    }

    public static class ExamImport {
        public String title;
        public List<QuestionImport> questions;
    }

    public static class QuestionImport {
        public String explanation;
        public List<ChoiceImport> choices;
    }

    public static class ChoiceImport {
        public String rationale;
    }
}
