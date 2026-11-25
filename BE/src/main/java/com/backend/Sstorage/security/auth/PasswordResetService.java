package com.backend.Sstorage.security.auth;

import com.backend.Sstorage.security.user.AppUser;
import com.backend.Sstorage.security.user.UserRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.MailException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
public class PasswordResetService {
    private static final Logger logger = LoggerFactory.getLogger(PasswordResetService.class);
    private static final Duration OTP_TTL = Duration.ofMinutes(10);
    private static final Duration RESET_TTL = Duration.ofMinutes(10);
    private static final Duration RESEND_DELAY = Duration.ofSeconds(60);
    private static final int MAX_ATTEMPTS = 5;

    private final PasswordResetChallengeRepository challenges;
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final ObjectProvider<JavaMailSender> mailSender;
    private final SecureRandom random = new SecureRandom();
    @Value("${app.mail.from:}")
    private String mailFrom;

    public PasswordResetService(PasswordResetChallengeRepository challenges, UserRepository users,
                                PasswordEncoder encoder, ObjectProvider<JavaMailSender> mailSender) {
        this.challenges = challenges;
        this.users = users;
        this.encoder = encoder;
        this.mailSender = mailSender;
    }

    @Transactional
    public void requestOtp(String requestedEmail) {
        String email = normalize(requestedEmail);
        Instant now = Instant.now();
        if (users.findByEmailIgnoreCase(email).isEmpty()) return;
        PasswordResetChallenge challenge = challenges.findById(email).orElse(null);
        if (challenge != null && challenge.getRequestedAt().plus(RESEND_DELAY).isAfter(now)) return;
        String otp = String.format("%06d", random.nextInt(1_000_000));
        if (challenge == null) challenge = new PasswordResetChallenge(email, encoder.encode(otp), now.plus(OTP_TTL), now);
        else challenge.replaceOtp(encoder.encode(otp), now.plus(OTP_TTL), now);
        challenges.save(challenge);

        JavaMailSender sender = mailSender.getIfAvailable();
        try {
            if (sender == null) throw new IllegalStateException("SMTP sender is unavailable");
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(email);
            if (mailFrom != null && !mailFrom.isBlank()) message.setFrom(mailFrom);
            message.setSubject("Mã xác thực đặt lại mật khẩu");
            message.setText("Mã OTP của bạn là " + otp + ". Mã có hiệu lực trong 10 phút. Nếu bạn không yêu cầu, hãy bỏ qua email này.");
            sender.send(message);
        } catch (MailException | IllegalStateException exception) {
            challenges.delete(challenge);
            String detail = exception.getMessage();
            logger.warn("Could not send password reset OTP email ({}): {}",
                    exception.getClass().getSimpleName(), detail == null ? "no additional details" : detail);
        }
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public String verifyOtp(String requestedEmail, String otp) {
        String email = normalize(requestedEmail);
        PasswordResetChallenge challenge = challenges.findById(email).orElse(null);
        Instant now = Instant.now();
        if (challenge == null || challenge.getOtpExpiresAt().isBefore(now)
                || challenge.getAttempts() >= MAX_ATTEMPTS
                || !encoder.matches(otp, challenge.getOtpHash())) {
            if (challenge != null) challenge.incrementAttempts();
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã OTP không hợp lệ hoặc đã hết hạn.");
        }
        String token = UUID.randomUUID().toString();
        challenge.authorizeReset(encoder.encode(token), now.plus(RESET_TTL));
        return token;
    }

    @Transactional
    public void complete(String requestedEmail, String token, String password, String confirmation) {
        if (!password.equals(confirmation)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mật khẩu xác nhận không khớp.");
        String email = normalize(requestedEmail);
        PasswordResetChallenge challenge = challenges.findById(email).orElse(null);
        Instant now = Instant.now();
        if (challenge == null || challenge.getResetTokenHash() == null
                || challenge.getResetTokenExpiresAt().isBefore(now)
                || !encoder.matches(token, challenge.getResetTokenHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Phiên đặt lại mật khẩu không hợp lệ hoặc đã hết hạn.");
        }
        AppUser user = users.findByEmailIgnoreCase(email).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không thể đặt lại mật khẩu cho tài khoản này."));
        user.changePassword(encoder.encode(password));
        users.save(user);
        challenges.delete(challenge);
    }

    private String normalize(String email) { return email.trim().toLowerCase(Locale.ROOT); }
}
