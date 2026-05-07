import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ResetExamData {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^:}]+):([^}]*)}");

    public static void main(String[] args) throws Exception {
        Properties properties = new Properties();
        try (var in = Files.newInputStream(Path.of("src/main/resources/application.properties"))) {
            properties.load(in);
        }
        String url = resolve(properties.getProperty("spring.datasource.url"));
        String user = resolve(properties.getProperty("spring.datasource.username"));
        String pass = resolve(properties.getProperty("spring.datasource.password"));

        try (Connection con = DriverManager.getConnection(url, user, pass);
             Statement st = con.createStatement()) {
            st.executeUpdate("""
                    truncate table attempt_answer_choices, attempt_answers, exam_attempts, wrong_notes,
                    question_choices, questions, exams restart identity cascade
                    """);
            System.out.println("reset_exam_data=ok");
        }
    }

    private static String resolve(String value) {
        Matcher matcher = PLACEHOLDER.matcher(value);
        if (!matcher.matches()) {
            return value;
        }
        return System.getenv().getOrDefault(matcher.group(1), matcher.group(2));
    }
}
