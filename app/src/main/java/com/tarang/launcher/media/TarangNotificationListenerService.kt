package com.tarang.launcher.media

import android.service.notification.NotificationListenerService

/**
 * A no-op notification listener. We don't read notifications — this exists only so the user can grant
 * "notification access", which is the permission that lets [android.media.session.MediaSessionManager]
 * report the device's active media sessions (used by the top-bar "now playing" chip).
 */
class TarangNotificationListenerService : NotificationListenerService()
