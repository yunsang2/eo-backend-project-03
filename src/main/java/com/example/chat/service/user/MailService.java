package com.example.chat.service.user;

import com.example.chat.domain.user.EmailVerificationEntity;
import com.example.chat.repository.user.EmailRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class MailService {

    private final JavaMailSender mailSender;
    private final EmailRepository verificationRepository;

    // 인증 번호 6자리 생성 및 메일 발송
    @Transactional
    public void sendVerificationEmail(String email) {
        String code = String.format("%06d", new Random().nextInt(1000000));
        // 5분 제한
        LocalDateTime expiryDate = LocalDateTime.now().plusMinutes(5);

        // 이미 요청한 적 있는 이메일이면 덮어쓰기, 아니면 새로 생성
        EmailVerificationEntity verification = verificationRepository.findById(email)
                .orElse(EmailVerificationEntity.builder()
                        .email(email)
                        .isVerified(false)
                        .build());

        verification.updateCode(code, expiryDate);
        verificationRepository.save(verification);

        // 실제 메일 발송 로직
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(email);
        message.setSubject("[AI 포털] 회원가입 인증 번호 안내");
        message.setText("인증 번호는 [" + code + "] 입니다. 5분 안에 입력해 주세요.");
        mailSender.send(message);
    }

    // 사용자가 입력한 번호 검증
    @Transactional
    public boolean verifyCode(String email, String inputCode) {
        EmailVerificationEntity verification = verificationRepository.findById(email)
                .orElseThrow(() -> new IllegalArgumentException("인증 요청 내역이 없습니다."));

        if (verification.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("인증 시간이 만료되었습니다. 다시 요청해 주세요.");
        }

        if (!verification.getVerificationCode().equals(inputCode)) {
            throw new IllegalArgumentException("인증 번호가 일치하지 않습니다.");
        }

        // 성공하면 isVerified = true
        verification.verifySuccess();
        return true;
    }
}