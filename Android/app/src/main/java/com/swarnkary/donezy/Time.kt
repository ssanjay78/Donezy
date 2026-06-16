package com.swarnkary.donezy

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

const val HOUR_MS = 60L * 60L * 1000L
const val DAY_MS  = 24L * HOUR_MS

fun formatDate(timestamp: Long): String =
    SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault()).format(Date(timestamp))

fun formatDateShort(timestamp: Long): String =
    SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(timestamp))
