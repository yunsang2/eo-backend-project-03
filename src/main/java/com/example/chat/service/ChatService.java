package com.example.chat.service;

import com.example.chat.domain.chat.ChatType;
import com.example.chat.domain.chat.ai.AiDto;
import com.example.chat.domain.chat.message.MessageDto;
import com.example.chat.domain.chat.message.MessageEntity;
import com.example.chat.domain.chat.message.MessageRole;
import com.example.chat.domain.chat.session.SessionDto;
import com.example.chat.domain.chat.session.SessionEntity;
import com.example.chat.domain.user.UserEntity;
import com.example.chat.repository.MessageRepository;
import com.example.chat.repository.SessionRepository;
import com.example.chat.repository.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final SessionRepository sessionRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final AiClient aiClient;

    @Transactional
    public MessageDto.Response askAi(String userId, String sessionId, MessageDto.Request request, ChatType type) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("사용자를 찾을 수 없습니다."));

        // 플랜 등급별 기능 제한 로직
        checkPlanPermission(user, type);

        // AI에게 질문하기 전 (prepareChat 호출)
        SessionEntity session = prepareChat(user, sessionId, request.content(), request.modelName(), type);

        // 프롬프트 엔지니어링 적용 (optimizePrompt 호출)
        String optimizedPrompt = optimizePrompt(request.content(), type);

        // AI 모델 및 API 호출
        String model = (request.modelName() == null || request.modelName().isBlank())
                ? "gpt-4o-mini" : request.modelName();

        if (user.getPlan().getName().equals("BASIC") && model.toLowerCase().contains("gpt-4")) {
            throw new IllegalArgumentException("BASIC 플랜에서는 gpt-4 모델을 사용할 수 없습니다.");
        }

        AiDto.Response aiResponse = aiClient.getAiAnswer(optimizedPrompt, model, type);

        // AI 답변 완료 후 (completeChat 호출)
        MessageEntity aiMessage = completeChat(user, session, aiResponse, model);

        return MessageDto.Response.fromEntity(aiMessage);
    }

    /**
     * 플랜 등급별 기능 제한 로직
     */
    private void checkPlanPermission(UserEntity user, ChatType type) {
        String planName = user.getPlan().getName();

        if (type == ChatType.SUMMARY && planName.equals("BASIC")) {
            throw new IllegalArgumentException("웹 페이지 요약 및 번역 기능은 PRO 플랜 이상부터 이용 가능합니다.");
        }
        if (type == ChatType.YOUTUBE && !planName.equals("PREMIUM")) {
            throw new IllegalArgumentException("유튜브 영상 요약 기능은 PREMIUM 플랜 전용 서비스입니다.");
        }
    }

    /**
     * 특강 자료의 프롬프트 엔지니어링 팁 반영
     */
    private String optimizePrompt(String content, ChatType type) {
        return switch (type) {
            case SUMMARY -> String.format(
                    "너는 웹 콘텐츠 요약 및 번역 전문가야. 아래의 내용을 핵심 위주로 요약하고 한국어로 번역해줘.\n\"\"\"\n%s\n\"\"\"", content);
            case YOUTUBE -> String.format(
                    "너는 유튜브 분석가야. 영상 스크립트 내용을 바탕으로 시간대별 주요 내용을 요약해줘.\n\"\"\"\n%s\n\"\"\"", content);
            default -> String.format(
                    "너는 '33Chat'의 지식인 어시스턴트야. 질문에 대해 단계별로 생각해서 답변해줘.\n\"\"\"\n%s\n\"\"\"", content);
        };
    }

    // AI에게 질문하기 전
    @Transactional
    public SessionEntity prepareChat(UserEntity user, String sessionId, String content, String modelName, ChatType type) {

        // 토큰 체크
        if (user.getRemainingTokens() <= 0) {
            throw new IllegalStateException("토큰이 부족합니다. 플랜을 업그레이드하거나 충전해주세요.");
        }

        // 모델 제한 (PlanService와 연동 후 보완)
        if (user.getPlan().getName().equals("BASIC") && modelName.contains("gpt-4")) {
            throw new IllegalArgumentException("현재 플랜에서 지원하지 않는 모델입니다.");
        }

        // 세션 생성 또는 조회
        SessionEntity session;
        if (sessionId == null || sessionId.isBlank()) {
            // 첫 질문이면 세션 생성 (첫 20자를 제목으로 언제든 수정가능)
            String title = content.length() > 20 ? content.substring(0, 20) : content;
            session = SessionEntity.builder()
                    .user(user)
                    .title(title)
                    .chatType(type)
                    .build();
            session = sessionRepository.save(session);
        } else {
            session = sessionRepository.findById(sessionId)
                    .orElseThrow(() -> new NoSuchElementException("존재하지 않는 채팅 세션입니다."));
        }

        // 사용자 질문 메시지 저장
        MessageEntity userMessage = MessageEntity.builder()
                .session(session)
                .role(MessageRole.USER)
                .content(content)
                .modelName(modelName)
                .usedTokens(0)
                .build();
        messageRepository.save(userMessage);

        return session;
    }

    // AI 답변 완료 후
    @Transactional
    public MessageEntity completeChat(UserEntity user, SessionEntity session, AiDto.Response aiResponse, String model) {

        // 답변 메시지 저장
        MessageEntity aiMessage = MessageEntity.builder()
                .session(session)
                .role(MessageRole.ASSISTANT)
                .content(aiResponse.answer())
                .modelName(model)
                .usedTokens(aiResponse.used_tokens())
                .build();

        MessageEntity savedMessage = messageRepository.save(aiMessage);

        // 토큰 차감
        user.decreaseTokens(aiResponse.used_tokens());

        return savedMessage;
    }

    // 내 채팅 목록 조회
    @Transactional(readOnly = true)
    public List<SessionDto.Response> getMySessions(String userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("사용자를 찾을 수 없습니다."));

        return sessionRepository.findAllByUserOrderByCreatedAtDesc(user).stream()
                .map(SessionDto.Response::fromEntity)
                .toList();
    }

    // 채팅방 상세 대화 내역 조회
    @Transactional(readOnly = true)
    public List<MessageDto.Response> getMessagesBySession(String sessionId) {
        SessionEntity session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new NoSuchElementException("세션을 찾을 수 없습니다."));

        return messageRepository.findAllBySessionOrderByCreatedAtAsc(session).stream()
                .map(MessageDto.Response::fromEntity)
                .toList();
    }

    // 채팅방 삭제
    @Transactional
    public void deleteSession(String sessionId) {
        SessionEntity session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new NoSuchElementException("삭제할 세션이 존재하지 않습니다."));
        sessionRepository.delete(session);
    }
}