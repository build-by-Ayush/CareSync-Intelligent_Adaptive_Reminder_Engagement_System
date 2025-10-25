package com.example.caresync.domain

object EventTypes {
    const val TRIGGERED = "TRIGGERED"   // Notification shown
    const val COMPLETED = "COMPLETED"   // User completed task

    const val SNOOZED = "SNOOZED"       // User snoozed
    const val DISMISSED = "DISMISSED"   // User explicitly dismissed
    const val IGNORED = "IGNORED"       // User swiped away
}
