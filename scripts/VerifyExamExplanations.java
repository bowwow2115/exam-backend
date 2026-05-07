import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VerifyExamExplanations {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^:}]+):([^}]*)}");

    public static void main(String[] args) throws Exception {
        Properties properties = new Properties();
        try (var in = Files.newInputStream(Path.of("src/main/resources/application.properties"))) {
            properties.load(in);
        }
        String url = resolve(properties.getProperty("spring.datasource.url"));
        String user = resolve(properties.getProperty("spring.datasource.username"));
        String pass = resolve(properties.getProperty("spring.datasource.password"));

        try (Connection con = DriverManager.getConnection(url, user, pass)) {
            try (PreparedStatement ps = con.prepareStatement("""
                    select e.id,
                           e.title,
                           count(distinct q.id) as question_count,
                           count(distinct c.id) as choice_count,
                           count(distinct case when q.explanation is not null and trim(q.explanation) <> '' then q.id end) as explained_questions,
                           count(distinct case when c.rationale is not null and trim(c.rationale) <> '' then c.id end) as rationale_choices
                    from exams e
                    left join questions q on q.exam_id = e.id
                    left join question_choices c on c.question_id = q.id
                    group by e.id, e.title
                    order by e.id
                    """)) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        System.out.printf(
                                "examId=%d title=%s questions=%d choices=%d explainedQuestions=%d rationaleChoices=%d%n",
                                rs.getLong("id"),
                                rs.getString("title"),
                                rs.getLong("question_count"),
                                rs.getLong("choice_count"),
                                rs.getLong("explained_questions"),
                                rs.getLong("rationale_choices")
                        );
                    }
                }
            }
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
