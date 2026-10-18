package com.zai.native_app.data.repo

import android.content.Context
import com.zai.native_app.data.db.AppDatabase
import com.zai.native_app.data.db.ChatEntity
import com.zai.native_app.data.db.MessageEntity
import kotlinx.coroutines.flow.Flow

class ChatRepository(context: Context) {

    private val dao = AppDatabase.getInstance(context).chatDao()

    fun observeChats(): Flow<List<ChatEntity>> = dao.observeChats()

    suspend fun getAllChats(): List<ChatEntity> = dao.getAllChats()

    suspend fun getChat(id: String): ChatEntity? = dao.getChat(id)

    suspend fun createChat(id: String, title: String = "New Chat", model: String = "glm-5.3"): ChatEntity {
        val chat = ChatEntity(id = id, title = title, model = model)
        dao.insertChat(chat)
        return chat
    }

    suspend fun deleteChat(id: String) = dao.deleteChat(id)

    suspend fun updateTitle(id: String, title: String) = dao.updateChatTitle(id, title)

    fun observeMessages(chatId: String): Flow<List<MessageEntity>> = dao.observeMessages(chatId)

    suspend fun getMessages(chatId: String): List<MessageEntity> = dao.getMessages(chatId)

    suspend fun addMessage(chatId: String, role: String, content: String, seq: Int) {
        val msg = MessageEntity(
            chatId = chatId,
            role = role,
            content = content,
            seq = seq
        )
        dao.saveMessageAndUpdateChat(msg)
    }

    suspend fun deleteMessages(chatId: String) = dao.deleteMessages(chatId)

    suspend fun countMessages(chatId: String): Int = dao.countMessages(chatId)
}
