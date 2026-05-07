package com.psh.exam.exam;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.psh.exam.account.Account;
import com.psh.exam.account.AccountRepository;
import com.psh.exam.account.AccountRole;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 권한이 있는 문제 데이터만 DB에 넣기 위한 JSON import runner입니다.
 * 외부 저작물을 앱이 직접 수집하지 않고, 사용자가 준비한 로컬 파일만 읽습니다.
 */
@Component
@ConditionalOnProperty(name = "app.import.exam-json")
@Order(1)
public class ExamImportRunner implements ApplicationRunner {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final ObjectMapper objectMapper;
    private final AccountRepository accountRepository;
    private final ExamRepository examRepository;
    private final PasswordEncoder passwordEncoder;
    private final String importPath;
    private final String additionalImportPaths;
    private final boolean skipExistingTitle;

    public ExamImportRunner(
            ObjectMapper objectMapper,
            AccountRepository accountRepository,
            ExamRepository examRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.import.exam-json}") String importPath,
            @Value("${app.import.exam-json-additional:}") String additionalImportPaths,
            @Value("${app.import.skip-existing-title:true}") boolean skipExistingTitle
    ) {
        this.objectMapper = objectMapper;
        this.accountRepository = accountRepository;
        this.examRepository = examRepository;
        this.passwordEncoder = passwordEncoder;
        this.importPath = importPath;
        this.additionalImportPaths = additionalImportPaths == null ? "" : additionalImportPaths;
        this.skipExistingTitle = skipExistingTitle;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws IOException {
        importExamFromPath(Path.of(importPath));
        for (String raw : additionalImportPaths.split(",")) {
            String trimmed = raw.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            importExamFromPath(Path.of(trimmed));
        }
    }

    private void importExamFromPath(Path path) throws IOException {
        if (!Files.isRegularFile(path)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "시험 import 파일을 찾을 수 없습니다: " + path);
        }

        ExamImportRequest request = objectMapper.readValue(path.toFile(), ExamImportRequest.class);
        String title = requireLength(request.title(), "시험 제목", 1, 200);
        if (skipExistingTitle && examRepository.existsByTitle(title)) {
            return;
        }

        Account creator = findOrCreateCreator(request.creator());
        Exam exam = new Exam(
                title,
                trimToNull(request.description()),
                validatePositiveOrNull(request.timeLimitMinutes(), "제한 시간"),
                Boolean.TRUE.equals(request.published()),
                creator
        );

        List<QuestionImportRequest> questions = request.questions();
        if (questions == null || questions.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "import 시험에는 최소 1개 이상의 문항이 필요합니다.");
        }

        int questionOrder = 1;
        for (QuestionImportRequest questionRequest : questions) {
            Question question = new Question(
                    questionOrder++,
                    requireLength(questionRequest.prompt(), "문항", 1, 5_000),
                    questionRequest.points() == null ? 1 : validatePositive(questionRequest.points(), "문항 배점"),
                    trimToNull(questionRequest.explanation())
            );

            List<ChoiceImportRequest> choices = questionRequest.choices();
            if (choices == null || choices.size() < 2) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "각 import 문항에는 최소 2개 이상의 선택지가 필요합니다.");
            }

            int correctCount = 0;
            int choiceOrder = 1;
            for (ChoiceImportRequest choiceRequest : choices) {
                boolean correct = Boolean.TRUE.equals(choiceRequest.correct());
                if (correct) {
                    correctCount++;
                }
                String rationale = trimToNull(choiceRequest.rationale());
                if (rationale != null && rationale.length() > 8_000) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "선택지 해설은 8000자 이하여야 합니다.");
                }
                question.addChoice(new Choice(
                        choiceOrder++,
                        requireLength(choiceRequest.text(), "선택지", 1, 2_000),
                        correct,
                        rationale
                ));
            }
            if (correctCount == 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "각 import 문항에는 최소 1개 이상의 정답 선택지가 필요합니다.");
            }
            exam.addQuestion(question);
        }

        examRepository.save(exam);
    }

    private Account findOrCreateCreator(AccountImportRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "creator 정보가 필요합니다.");
        }

        String email = normalizeEmail(request.email());
        String displayName = requireLength(request.displayName(), "creator 표시 이름", 1, 80);
        if (email == null || !EMAIL_PATTERN.matcher(email).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "creator 이메일이 유효하지 않습니다.");
        }

        return accountRepository.findByEmail(email)
                .orElseGet(() -> {
                    String password = trimToNull(request.password());
                    if (password == null || password.length() < 8) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "새 creator 비밀번호는 8자 이상이어야 합니다.");
                    }
                    return accountRepository.save(new Account(
                            email,
                            passwordEncoder.encode(password),
                            displayName,
                            AccountRole.ADMIN
                    ));
                });
    }

    private Integer validatePositiveOrNull(Integer value, String fieldName) {
        if (value == null) {
            return null;
        }
        return validatePositive(value, fieldName);
    }

    private int validatePositive(int value, String fieldName) {
        if (value <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + "은 1 이상이어야 합니다.");
        }
        return value;
    }

    private String requireLength(String value, String fieldName, int min, int max) {
        String trimmed = trimToNull(value);
        if (trimmed == null || trimmed.length() < min || trimmed.length() > max) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    fieldName + "은 " + min + "자 이상 " + max + "자 이하여야 합니다."
            );
        }
        return trimmed;
    }

    private String normalizeEmail(String value) {
        String email = trimToNull(value);
        return email == null ? null : email.toLowerCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record ExamImportRequest(
            AccountImportRequest creator,
            String title,
            String description,
            Integer timeLimitMinutes,
            Boolean published,
            List<QuestionImportRequest> questions
    ) {
    }

    public record AccountImportRequest(
            String email,
            String password,
            String displayName
    ) {
    }

    public record QuestionImportRequest(
            String prompt,
            Integer points,
            String explanation,
            List<ChoiceImportRequest> choices
    ) {
    }

    public record ChoiceImportRequest(
            String text,
            Boolean correct,
            String rationale
    ) {
    }
}
