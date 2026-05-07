import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VerifyExam2Import {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^:}]+):([^}]*)}");
    private static final String TITLE = "AWS Developer Practice Exam 2 (Parsed)";

    public static void main(String[] args) throws Exception {
        Properties properties = new Properties();
        try (var in = Files.newInputStream(Path.of("src/main/resources/application.properties"))) {
            properties.load(in);
        }
        String url = resolve(properties.getProperty("spring.datasource.url"));
        String user = resolve(properties.getProperty("spring.datasource.username"));
        String pass = resolve(properties.getProperty("spring.datasource.password"));

        try (Connection con = DriverManager.getConnection(url, user, pass)) {
            try (PreparedStatement ps = con.prepareStatement(
                    "select id from exams where title = ? order by id desc limit 1"
            )) {
                ps.setString(1, TITLE);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        System.out.println("not_found");
                        return;
                    }
                    long examId = rs.getLong(1);
                    System.out.println("examId=" + examId);
                    System.out.println("questions=" + count(con, "select count(*) from questions where exam_id = ?", examId));
                    System.out.println("choices=" + count(con,
                            "select count(*) from question_choices c join questions q on c.question_id = q.id where q.exam_id = ?",
                            examId));
                }
            }
        }
    }

    private static long count(Connection con, String sql, long id) throws Exception {
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private static String resolve(String value) {
        Matcher m = PLACEHOLDER.matcher(value);
        if (!m.matches()) {
            return value;
        }
        return System.getenv().getOrDefault(m.group(1), m.group(2));
    }
}
