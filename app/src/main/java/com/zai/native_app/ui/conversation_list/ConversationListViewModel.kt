package com.zai.native_app.ui.conversation_list

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zai.native_app.data.db.ChatEntity
import com.zai.native_app.data.repo.ChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ConversationListViewModel : ViewModel() {

    private val _chats = MutableStateFlow<List<ChatEntity>>(emptyList())
    val chats: StateFlow<List<ChatEntity>> = _chats.asStateFlow()

    private var repository: ChatRepository? = null

    fun init(context: Context) {
        if (repository == null) {
            repository = ChatRepository(context)
            viewModelScope.launch {
                repository?.observeChats()?.collectLatest { list ->
                    _chats.value = list
                }
            }
        }
    }

    fun deleteChat(id: String) {
        viewModelScope.launch {
            repository?.deleteChat(id)
        }
    }
}
