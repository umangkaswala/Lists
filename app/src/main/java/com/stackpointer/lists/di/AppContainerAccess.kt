package com.stackpointer.lists.di

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.stackpointer.lists.AppContainer
import com.stackpointer.lists.ListsApplication

@Composable
fun currentAppContainer(): AppContainer {
    val context = LocalContext.current.applicationContext as ListsApplication
    return context.container
}
