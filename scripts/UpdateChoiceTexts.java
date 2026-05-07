import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UpdateChoiceTexts {

    private static final String EXAM_TITLE = "AWS Developer Associate Practice Exam - Korean";
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^:}]+):([^}]*)}");
    private static final Pattern ENTRY = Pattern.compile(
            "\\{\\s*\"questionSortOrder\"\\s*:\\s*(\\d+)\\s*,\\s*\"choiceSortOrder\"\\s*:\\s*(\\d+)\\s*,\\s*\"text\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"\\s*\\}"
    );

    record Row(int questionSortOrder, int choiceSortOrder, String text) {}

    public static void main(String[] args) throws Exception {
        Properties properties = loadProperties();
        String url = resolve(properties.getProperty("spring.datasource.url"));
        String username = resolve(properties.getProperty("spring.datasource.username"));
        String password = resolve(properties.getProperty("spring.datasource.password"));
        List<Row> rows = parseRows(Path.of("data/choices.retranslated.json"));
        if (rows.size() != 1619) {
            throw new IllegalStateException("Expected 1619 choices but got " + rows.size());
        }

        try (Connection connection = DriverManager.getConnection(url, username, password)) {
            connection.setAutoCommit(false);
            long examId = findExamId(connection);

            int updated = 0;
            try (PreparedStatement statement = connection.prepareStatement(
                    "update question_choices c set choice_text = ? from questions q " +
                            "where c.question_id = q.id and q.exam_id = ? and q.sort_order = ? and c.sort_order = ?"
            )) {
                for (Row row : rows) {
                    statement.setString(1, row.text());
                    statement.setLong(2, examId);
                    statement.setInt(3, row.questionSortOrder());
                    statement.setInt(4, row.choiceSortOrder());
                    updated += statement.executeUpdate();
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

    private static List<Row> parseRows(Path path) throws IOException {
        String content = Files.readString(path);
        List<Row> rows = new ArrayList<>();
        Matcher matcher = ENTRY.matcher(content);
        while (matcher.find()) {
            int q = Integer.parseInt(matcher.group(1));
            int c = Integer.parseInt(matcher.group(2));
            String text = unescapeJson(matcher.group(3));
            rows.add(new Row(q, c, text));
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
