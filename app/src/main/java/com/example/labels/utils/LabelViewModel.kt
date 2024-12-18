package com.example.labels.utils

import androidx.lifecycle.ViewModel
import com.example.labels.LabelResponse

class LabelViewModel : ViewModel() {
    val labels = LinkedHashMap<String, LabelResponse>()
}