package com.datanexus.datanexus.service;

import com.datanexus.datanexus.dto.conversation.*;
import com.datanexus.datanexus.entity.Conversation;
import com.datanexus.datanexus.entity.Message;
import com.datanexus.datanexus.entity.User;
import com.datanexus.datanexus.exception.ApiException;
import com.datanexus.datanexus.repository.ConversationRepository;
import com.datanexus.datanexus.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;

    public List<ConversationDto> getUserConversations(User user, int limit, int offset, String sort) {
        Sort sorting = "oldest".equals(sort)
                ? Sort.by(Sort.Direction.ASC, "updatedAt")
                : Sort.by(Sort.Direction.DESC, "updatedAt");

        PageRequest pageable = PageRequest.of(offset / Math.max(limit, 1), limit, sorting);

        return conversationRepository.findByUserIdOrderByUpdatedAtDesc(user.getId(), pageable)
                .stream()
                .map(this::toListDto)
                .collect(Collectors.toList());
    }

    public long countUserConversations(User user) {
        return conversationRepository.countByUserId(user.getId());
    }

    @Transactional
    public ConversationDto createConversation(CreateConversationRequest request, User user) {
        Conversation conversation = Conversation.builder()
                .name(request.getName())
                .connectionIds(request.getConnectionIds() != null ? request.getConnectionIds() : new ArrayList<>())
                .user(user)
                .shared(false)
                .build();

        conversation = conversationRepository.save(conversation);
        return toDetailDto(conversation);
    }

    public ConversationDto getConversation(String conversationId, User user) {
        Conversation conversation = conversationRepository.findByIdAndUserId(conversationId, user.getId())
                .orElseThrow(() -> ApiException.notFound("CONVERSATION_NOT_FOUND", "The requested conversation could not be found"));
        return toDetailDto(conversation);
    }

    @Transactional
    public ConversationDto updateConversation(String conversationId, UpdateConversationRequest request, User user) {
        Conversation conversation = conversationRepository.findByIdAndUserId(conversationId, user.getId())
                .orElseThrow(() -> ApiException.notFound("CONVERSATION_NOT_FOUND", "The requested conversation could not be found"));

        if (request.getName() != null) conversation.setName(request.getName());
        if (request.getConnectionIds() != null) conversation.setConnectionIds(request.getConnectionIds());

        conversation = conversationRepository.save(conversation);

        return ConversationDto.builder()
                .id(conversation.getId())
                .name(conversation.getName())
                .connectionIds(conversation.getConnectionIds())
                .updatedAt(conversation.getUpdatedAt())
                .build();
    }

    @Transactional
    public MessageDto addMessage(String conversationId, AddMessageRequest request, User user) {
        Conversation conversation = conversationRepository.findByIdAndUserId(conversationId, user.getId())
                .orElseThrow(() -> ApiException.notFound("CONVERSATION_NOT_FOUND", "The requested conversation could not be found"));

        Message message = Message.builder()
                .role(request.getRole())
                .content(request.getContent())
                .conversation(conversation)
                .build();

        message = messageRepository.save(message);

        return MessageDto.builder()
                .id(message.getId())
                .role(message.getRole())
                .content(message.getContent())
                .timestamp(message.getTimestamp())
                .build();
    }

    @Transactional
    public void deleteConversation(String conversationId, User user) {
        Conversation conversation = conversationRepository.findByIdAndUserId(conversationId, user.getId())
                .orElseThrow(() -> ApiException.notFound("CONVERSATION_NOT_FOUND", "The requested conversation could not be found"));
        conversationRepository.delete(conversation);
    }

    @Transactional
    public ConversationDto shareConversation(String conversationId, User user) {
        Conversation conversation = conversationRepository.findByIdAndUserId(conversationId, user.getId())
                .orElseThrow(() -> ApiException.notFound("CONVERSATION_NOT_FOUND", "The requested conversation could not be found"));

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
        Conversation conversation = conversationRepository.findByShareId(shareId)
                .orElseThrow(() -> ApiException.notFound("NOT_FOUND", "Shared conversation not found"));
        return toDetailDto(conversation);
    }

    @Transactional
    public void unshareConversation(String conversationId, User user) {
        Conversation conversation = conversationRepository.findByIdAndUserId(conversationId, user.getId())
                .orElseThrow(() -> ApiException.notFound("CONVERSATION_NOT_FOUND", "The requested conversation could not be found"));
        conversation.setShared(false);
        conversation.setShareId(null);
        conversationRepository.save(conversation);
    }

    private ConversationDto toListDto(Conversation conversation) {
        return ConversationDto.builder()
                .id(conversation.getId())
                .name(conversation.getName())
                .connectionIds(conversation.getConnectionIds())
                .messageCount(conversation.getMessages().size())
                .createdAt(conversation.getCreatedAt())
                .updatedAt(conversation.getUpdatedAt())
                .isShared(conversation.isShared())
                .shareId(conversation.getShareId())
                .build();
    }

    private ConversationDto toDetailDto(Conversation conversation) {
        List<MessageDto> messageDtos = conversation.getMessages().stream()
                .map(m -> MessageDto.builder()
                        .id(m.getId())
                        .role(m.getRole())
                        .content(m.getContent())
                        .timestamp(m.getTimestamp())
                        .build())
                .collect(Collectors.toList());

        return ConversationDto.builder()
                .id(conversation.getId())
                .name(conversation.getName())
                .connectionIds(conversation.getConnectionIds())
                .messages(messageDtos)
                .messageCount(messageDtos.size())
                .createdAt(conversation.getCreatedAt())
                .updatedAt(conversation.getUpdatedAt())
                .isShared(conversation.isShared())
                .shareId(conversation.getShareId())
                .build();
    }
}
