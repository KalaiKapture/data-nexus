package com.datanexus.datanexus.service;

import com.datanexus.datanexus.dto.conversation.*;
import com.datanexus.datanexus.entity.Activities;
import com.datanexus.datanexus.entity.Conversation;
import com.datanexus.datanexus.entity.Message;
import com.datanexus.datanexus.entity.User;
import com.datanexus.datanexus.exception.ApiException;
import com.datanexus.datanexus.repository.ConversationRepository;
import com.datanexus.datanexus.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;

    public List<ConversationDto> getUserConversations(User user, int limit, int offset, String sort) {
        String orderDir = "oldest".equals(sort) ? "ASC" : "DESC";

        List<Conversation> conversations = conversationRepository.findByUserIdOrdered(user.getId(), orderDir, limit, offset);

        return conversations.stream()
                .map(this::toListDto)
                .collect(Collectors.toList());
    }

    public long countUserConversations(User user) {
        return conversationRepository.countByUserId(user.getId());
    }

    public ConversationDto createConversation(CreateConversationRequest request, User user) {
        Conversation conversation = Conversation.builder()
                .name(request.getName())
                .connectionIds(toConnectionIdString(request.getConnectionIds()))
                .user(user.getId())
                .shared(false)
                .build();

        conversation = conversationRepository.save(conversation);
        return toDetailDto(conversation);
    }

    public ConversationDto getConversation(Long conversationId, User user) {
        Conversation conversation = findConversationByIdAndUser(conversationId, user.getId());
        return toDetailDto(conversation);
    }

    public ConversationDto updateConversation(Long conversationId, UpdateConversationRequest request, User user) {
        Conversation conversation = findConversationByIdAndUser(conversationId, user.getId());

        if (request.getName() != null) conversation.setName(request.getName());
        if (request.getConnectionIds() != null)
            conversation.setConnectionIds(toConnectionIdString(request.getConnectionIds()));

        conversation = conversationRepository.save(conversation);

        return ConversationDto.builder()
                .id(conversation.getId())
                .name(conversation.getName())
                .connectionIds(parseConnectionIds(conversation.getConnectionIds()))
                .updatedAt(conversation.getUpdatedAt())
                .build();
    }

    public MessageDto addMessage(Long conversationId, AddMessageRequest request, User user) {

        Message message = Message.builder()
                .content(request.getContent())
                .sentByUser(request.isSentByUser())
                .conversation(conversationId)
                .build();

        message = messageRepository.save(message);

        return MessageDto.builder()
                .id(message.getId())
                .content(message.getContent())
                .sentByUser(message.isSentByUser())
                .createdAt(message.getCreatedAt())
                .updatedAt(message.getUpdatedAt())
                .build();
    }

    public List<MessageDto> getMessages(Long conversationId, User user) {
        findConversationByIdAndUser(conversationId, user.getId());
        List<Message> messages = messageRepository.findByConversationId(conversationId);
        return messages.stream()
                .map(this::toMessageDto)
                .collect(Collectors.toList());
    }

    public void deleteConversation(Long conversationId, User user) {
        Conversation conversation = findConversationByIdAndUser(conversationId, user.getId());
        conversationRepository.delete(conversation);
    }

    public ConversationDto shareConversation(Long conversationId, User user) {
        Conversation conversation = findConversationByIdAndUser(conversationId, user.getId());

        String shareId = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        conversation.setShared(true);
        conversation.setShareId(shareId);
        conversation = conversationRepository.save(conversation);

        return ConversationDto.builder()
                .id(conversation.getId())
                .isShared(true)
                .shareId(shareId)
                .build();
    }

    public ConversationDto getSharedConversation(String shareId) {
        Conversation conversation = conversationRepository.findByShareId(shareId);
        if (conversation == null) {
            throw ApiException.notFound("NOT_FOUND", "Shared conversation not found");
        }
        return toDetailDto(conversation);
    }

    public void unshareConversation(Long conversationId, User user) {
        Conversation conversation = findConversationByIdAndUser(conversationId, user.getId());
        conversation.setShared(false);
        conversation.setShareId(null);
        conversationRepository.save(conversation);
    }

    private Conversation findConversationByIdAndUser(Long conversationId, Long userId) {
        Conversation conversation = conversationRepository.findByIdAndUserId(conversationId, userId);
        if (conversation == null) {
            throw ApiException.notFound("CONVERSATION_NOT_FOUND", "The requested conversation could not be found");
        }
        return conversation;
    }

    private ConversationDto toListDto(Conversation conversation) {
        return ConversationDto.builder()
                .id(conversation.getId())
                .name(conversation.getName())
                .connectionIds(parseConnectionIds(conversation.getConnectionIds()))
                .messageCount((int) messageRepository.countByConversationId(conversation.getId()))
                .createdAt(conversation.getCreatedAt())
                .updatedAt(conversation.getUpdatedAt())
                .isShared(conversation.isShared())
                .shareId(conversation.getShareId())
                .build();
    }

    private ConversationDto toDetailDto(Conversation conversation) {
        List<MessageDto> messageDtos = messageRepository.findByConversationId(conversation.getId()).stream()
                .map(this::toMessageDto)
                .collect(Collectors.toList());

        return ConversationDto.builder()
                .id(conversation.getId())
                .name(conversation.getName())
                .connectionIds(parseConnectionIds(conversation.getConnectionIds()))
                .messages(messageDtos)
                .messageCount(messageDtos.size())
                .createdAt(conversation.getCreatedAt())
                .updatedAt(conversation.getUpdatedAt())
                .isShared(conversation.isShared())
                .shareId(conversation.getShareId())
                .build();
    }

    private List<Long> parseConnectionIds(String connectionIds) {
        if (connectionIds == null || connectionIds.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(connectionIds.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::valueOf)
                .collect(Collectors.toList());
    }

    private String toConnectionIdString(List<Long> connectionIds) {
        if (connectionIds == null || connectionIds.isEmpty()) {
            return "";
        }
        return connectionIds.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
    }

    private MessageDto toMessageDto(Message m) {
        return MessageDto.builder()
                .id(m.getId())
                .content(m.getContent())
                .sentByUser(m.isSentByUser())
                .type(m.getType())
                .createdAt(m.getCreatedAt())
                .updatedAt(m.getUpdatedAt())
                .build();
    }

    public List<Activities> getActivities(Long conversationId, User user) {
        findConversationByIdAndUser(conversationId, user.getId());
        List<Activities> activities = messageRepository.findActivityByConversation(conversationId);
        return activities;
    }
}
