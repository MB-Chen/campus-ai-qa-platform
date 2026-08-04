package sparkx.sparkshop.controller.chat;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sparkx.sparkshop.campus.agent.CampusAgentService;

/**
 * 校园智能问答平台 - 聊天端点
 *
 * POST /api/campus/chat
 * 请求：{ "question": "我下学期有什么课" }
 * 响应：{ "answer": "...", "status": "OK" }
 *
 * 注意：该路径不在 LoginInterceptor 放行白名单内，调用需携带登录 token。
 */
@Slf4j
@RestController
@RequestMapping("/api/campus")
@RequiredArgsConstructor
public class CampusChatController {

    private final CampusAgentService campusAgentService;

    @PostMapping("/chat")
    public ChatResponse chat(@RequestBody ChatRequest request) {
        log.info("[校园平台] 收到问题: {}", request.getQuestion());
        String answer = campusAgentService.chat(request.getQuestion());
        return new ChatResponse(answer, "OK");
    }

    @Data
    public static class ChatRequest {
        private String question;
    }

    @Value
    public static class ChatResponse {
        String answer;
        String status;
    }
}
