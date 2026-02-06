package com.datanexus.datanexus.repository;

import com.datanexus.datanexus.entity.Message;
import com.datanexus.datanexus.utils.PSQLUtil;
import org.springframework.stereotype.Repository;

@Repository
public class MessageRepository {

    public Message save(Message message) {
        return PSQLUtil.saveOrUpdateWithReturn(message);
    }
}
