package com.example.chat.service;

import com.example.chat.domain.chat.ChatType;
import com.example.chat.domain.chat.ai.AiDto;
import com.example.chat.domain.chat.message.MessageDto;
import com.example.chat.domain.chat.message.MessageEntity;
import com.example.chat.domain.chat.message.MessageRole;
import com.example.chat.domain.chat.session.SessionDto;
import com.example.chat.domain.chat.session.SessionEntity;
import com.example.chat.domain.plan.PlanEntity;
import com.example.chat.domain.user.UserEntity;
import com.example.chat.repository.MessageRepository;
import com.example.chat.repository.SessionRepository;
import com.example.chat.repository.user.UserRepository;
import com.example.chat.service.AiClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @InjectMocks
    private ChatService chatService;

    @Mock private SessionRepository sessionRepository;
    @Mock private MessageRepository messageRepository;
    @Mock private UserRepository userRepository;
    @Mock private AiClient aiClient;

    @Test
    @DisplayName("채팅 준비 성공 - 새 세션과 메시지가 저장된다")
    void prepareChat_success_newSession() {
        // given
        PlanEntity plan = PlanEntity.builder().name("BASIC").build();
        UserEntity user = UserEntity.builder()
                .id("user-1")
                .remainingTokens(100)
                .plan(plan)
                .build();

        // 세션 저장 시 self-return
        when(sessionRepository.save(any(SessionEntity.class))).thenAnswer(i -> i.getArguments()[0]);

        // when
        SessionEntity result = chatService.prepareChat(user, null, "안녕하세요 AI님!", "gpt-3.5", ChatType.CHAT);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getTitle()).isEqualTo("안녕하세요 AI님!");
        assertThat(result.getChatType()).isEqualTo(ChatType.CHAT);
        verify(sessionRepository, times(1)).save(any(SessionEntity.class));
        verify(messageRepository, times(1)).save(any(MessageEntity.class));
    }

    @Test
    @DisplayName("채팅 준비 실패 - 토큰이 부족하면 예외가 발생한다")
    void prepareChat_fail_outOfTokens() {
        // given
        UserEntity user = UserEntity.builder()
                .id("user-1")
                .remainingTokens(0) // 토큰 없음
                .build();

        // when & then
        assertThatThrownBy(() -> chatService.prepareChat(user, null, "질문있어요", "gpt-3.5", ChatType.CHAT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("토큰이 부족합니다");
    }

    @Test
    @DisplayName("채팅 준비 실패 - BASIC 플랜은 GPT-4를 사용할 수 없다")
    void prepareChat_fail_planRestriction() {
        // given
        PlanEntity basicPlan = PlanEntity.builder().name("BASIC").build();
        UserEntity user = UserEntity.builder()
                .id("user-1")
                .remainingTokens(100)
                .plan(basicPlan)
                .build();

        // when & then
        assertThatThrownBy(() -> chatService.prepareChat(user, null, "어려운 질문", "gpt-4", ChatType.CHAT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("지원하지 않는 모델입니다");
    }

    @Test
    @DisplayName("채팅 완료 성공 - AI 답변이 저장되고 토큰이 차감된다")
    void completeChat_success() {
        // given
        UserEntity user = spy(UserEntity.builder()
                .id("user-1")
                .remainingTokens(100)
                .build());

        SessionEntity session = SessionEntity.builder().id("session-1").build();
        AiDto.Response aiResponse = new AiDto.Response("네, 무엇을 도와드릴까요?", 10);

        when(messageRepository.save(any(MessageEntity.class))).thenAnswer(i -> i.getArguments()[0]);

        // when
        MessageEntity result = chatService.completeChat(user, session, aiResponse, "gpt-3.5");

        // then
        assertThat(result.getContent()).isEqualTo("네, 무엇을 도와드릴까요?");
        assertThat(result.getUsedTokens()).isEqualTo(10);
        verify(messageRepository, times(1)).save(any(MessageEntity.class));
        // UserEntity의 토큰 차감 확인
        assertThat(user.getRemainingTokens()).isEqualTo(90);
    }

    @Test
    @DisplayName("세션 삭제 성공 - 존재하는 세션을 삭제하면 repository.delete가 호출된다")
    void deleteSession_success() {
        // given
        String sessionId = "session-123";
        SessionEntity session = SessionEntity.builder().id(sessionId).build();

        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

        // when
        chatService.deleteSession(sessionId);

        // then
        verify(sessionRepository, times(1)).delete(session);
    }

    @Test
    @DisplayName("세션 삭제 실패 - 존재하지 않는 세션 ID인 경우 예외 발생")
    void deleteSession_fail_notFound() {
        // given
        String sessionId = "non-existent-id";
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> chatService.deleteSession(sessionId))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("존재하지 않습니다");

        verify(sessionRepository, never()).delete(any());
    }

    @Test
    @DisplayName("AI 질문 성공 - 권한이 충분하면 전체 흐름(세션/메시지 저장, AI 호출, 토큰 차감)이 정상 동작한다")
    void askAi_success() {
        // given
        PlanEntity premiumPlan = PlanEntity.builder().name("PREMIUM").build();
        UserEntity user = spy(UserEntity.builder()
                .id("user-1")
                .remainingTokens(1000)
                .plan(premiumPlan)
                .build());

        MessageDto.Request request = new MessageDto.Request("스프링부트가 뭐야?", "gpt-4o-mini");

        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));

        // prepareChat 내부: 세션 및 사용자 메시지 저장
        SessionEntity session = SessionEntity.builder().id("session-1").build();
        when(sessionRepository.save(any(SessionEntity.class))).thenReturn(session);

        // aiClient 호출
        AiDto.Response aiResponse = new AiDto.Response("자바 웹 프레임워크입니다.", 25);
        when(aiClient.getAiAnswer(anyString(), anyString(), any(ChatType.class))).thenReturn(aiResponse);

        // completeChat 내부: AI 답변 메시지 저장
        MessageEntity aiMessage = MessageEntity.builder()
                .id("msg-1")
                .role(MessageRole.ASSISTANT)
                .content("자바 웹 프레임워크입니다.")
                .usedTokens(25)
                .build();
        // messageRepository.save()가 2번 호출(유저 질문, AI 답변)되므로 return 값 설정에 유의
        when(messageRepository.save(any(MessageEntity.class))).thenReturn(aiMessage);

        // when
        MessageDto.Response response = chatService.askAi("user-1", null, request, ChatType.CHAT);

        // then
        assertThat(response).isNotNull();
        assertThat(response.content()).isEqualTo("자바 웹 프레임워크입니다.");
        assertThat(response.usedTokens()).isEqualTo(25);

        // 검증: messageRepository.save가 정확히 2번(사용자 질문 1번, AI 답변 1번) 호출되었는가
        verify(messageRepository, times(2)).save(any(MessageEntity.class));
    }

    @Test
    @DisplayName("AI 질문 실패 - BASIC 플랜으로 웹 페이지 요약(SUMMARY) 요청 시 예외 발생")
    void askAi_fail_summaryBasicPlan() {
        // given
        PlanEntity basicPlan = PlanEntity.builder().name("BASIC").build();
        UserEntity user = UserEntity.builder().id("user-1").plan(basicPlan).build();
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));

        MessageDto.Request request = new MessageDto.Request("http://example.com 요약해줘", "gpt-4o-mini");

        // when & then
        assertThatThrownBy(() -> chatService.askAi("user-1", null, request, ChatType.SUMMARY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PRO 플랜 이상부터 이용 가능");
    }

    @Test
    @DisplayName("AI 질문 실패 - PRO 플랜으로 유튜브 요약(YOUTUBE) 요청 시 예외 발생")
    void askAi_fail_youtubeProPlan() {
        // given
        PlanEntity proPlan = PlanEntity.builder().name("PRO").build();
        UserEntity user = UserEntity.builder().id("user-1").plan(proPlan).build();
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));

        MessageDto.Request request = new MessageDto.Request("https://youtube.com/watch?v=123 요약해줘", "gpt-4o-mini");

        // when & then
        assertThatThrownBy(() -> chatService.askAi("user-1", null, request, ChatType.YOUTUBE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PREMIUM 플랜 전용 서비스");
    }

    @Test
    @DisplayName("내 채팅 목록 조회 성공 - 최신순으로 유저의 세션 목록을 반환한다")
    void getMySessions_success() {
        // given
        UserEntity user = UserEntity.builder().id("user-1").build();
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));

        SessionEntity session1 = SessionEntity.builder().id("s-1").title("첫 번째 질문").chatType(ChatType.CHAT).build();
        SessionEntity session2 = SessionEntity.builder().id("s-2").title("두 번째 질문").chatType(ChatType.SUMMARY).build();

        when(sessionRepository.findAllByUserOrderByCreatedAtDesc(user)).thenReturn(List.of(session1, session2));

        // when
        List<SessionDto.Response> result = chatService.getMySessions("user-1");

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).title()).isEqualTo("첫 번째 질문");
        assertThat(result.get(1).title()).isEqualTo("두 번째 질문");
        assertThat(result.get(1).chatType()).isEqualTo(ChatType.SUMMARY);
    }

    @Test
    @DisplayName("대화 내역 조회 성공 - 세션에 속한 메시지들을 순서대로 반환한다")
    void getMessagesBySession_success() {
        // given
        SessionEntity session = SessionEntity.builder().id("s-1").build();
        when(sessionRepository.findById("s-1")).thenReturn(Optional.of(session));

        MessageEntity msg1 = MessageEntity.builder().id("m-1").role(MessageRole.USER).content("안녕").build();
        MessageEntity msg2 = MessageEntity.builder().id("m-2").role(MessageRole.ASSISTANT).content("안녕하세요!").build();
        when(messageRepository.findAllBySessionOrderByCreatedAtAsc(session)).thenReturn(List.of(msg1, msg2));

        // when
        List<MessageDto.Response> result = chatService.getMessagesBySession("s-1");

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).role()).isEqualTo("USER");
        assertThat(result.get(1).role()).isEqualTo("ASSISTANT");
        assertThat(result.get(1).content()).isEqualTo("안녕하세요!");
    }
}