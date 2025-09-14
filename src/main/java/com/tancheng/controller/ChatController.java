package com.tancheng.controller;



import com.tancheng.service.ShopTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ai")
public class ChatController {
    @RequestMapping("/test")
    public String test() {
        return "应用运行正常";
    }


    @Autowired
    ChatClient chatClient;

    @Autowired
    private ChatModel chatModel;

    @Autowired
    private ShopTools shopTools;

    @RequestMapping(value = "/chat", produces = "text/html;charset=UTF-8")
    public String chat(String prompt, String chatId) {

        return chatClient.prompt().user(prompt)
//                .advisors(a -> a.param(CONVERSATION_ID, chatId))
                .tools(shopTools)
                .call().content();
    }
}
