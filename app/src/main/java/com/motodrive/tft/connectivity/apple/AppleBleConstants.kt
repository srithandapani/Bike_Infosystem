package com.motodrive.tft.connectivity.apple

import java.util.UUID

object AppleBleConstants {

    // Standard Client Characteristic Configuration Descriptor (CCCD)
    val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    // =========================================================================
    // Apple Notification Center Service (ANCS) UUIDs
    // =========================================================================
    val ANCS_SERVICE_UUID: UUID = UUID.fromString("7905F431-B5CE-4E99-A40F-4B1E122D00D0")
    val ANCS_NOTIFICATION_SOURCE_UUID: UUID = UUID.fromString("9FBF120D-6301-42D9-8C58-25E699A21DBD")
    val ANCS_CONTROL_POINT_UUID: UUID = UUID.fromString("69D1D8F3-45E1-49A8-9821-9BBDFDAAD9D9")
    val ANCS_DATA_SOURCE_UUID: UUID = UUID.fromString("22EAC6E9-24D6-4BB5-BE44-B36ACE7C7BFB")

    // ANCS Event IDs
    const val EVENT_ID_NOTIFICATION_ADDED = 0
    const val EVENT_ID_NOTIFICATION_MODIFIED = 1
    const val EVENT_ID_NOTIFICATION_REMOVED = 2

    // ANCS Category IDs
    const val CATEGORY_ID_OTHER = 0
    const val CATEGORY_ID_INCOMING_CALL = 1
    const val CATEGORY_ID_MISSED_CALL = 2
    const val CATEGORY_ID_VOICEMAIL = 3
    const val CATEGORY_ID_SOCIAL = 4
    const val CATEGORY_ID_SCHEDULE = 5
    const val CATEGORY_ID_EMAIL = 6
    const val CATEGORY_ID_NEWS = 7
    const val CATEGORY_ID_HEALTH = 8
    const val CATEGORY_ID_BUSINESS = 9
    const val CATEGORY_ID_LOCATION = 10
    const val CATEGORY_ID_ENTERTAINMENT = 11

    // ANCS Command IDs
    const val COMMAND_ID_GET_NOTIFICATION_ATTRIBUTES = 0.toByte()
    const val COMMAND_ID_GET_APP_ATTRIBUTES = 1.toByte()
    const val COMMAND_ID_PERFORM_NOTIFICATION_ACTION = 2.toByte()

    // ANCS Notification Attribute IDs
    const val NOTIFICATION_ATTRIBUTE_ID_APP_IDENTIFIER = 0.toByte()
    const val NOTIFICATION_ATTRIBUTE_ID_TITLE = 1.toByte()
    const val NOTIFICATION_ATTRIBUTE_ID_SUBTITLE = 2.toByte()
    const val NOTIFICATION_ATTRIBUTE_ID_MESSAGE = 3.toByte()
    const val NOTIFICATION_ATTRIBUTE_ID_MESSAGE_SIZE = 4.toByte()
    const val NOTIFICATION_ATTRIBUTE_ID_DATE = 5.toByte()

    // =========================================================================
    // Apple Media Service (AMS) UUIDs
    // =========================================================================
    val AMS_SERVICE_UUID: UUID = UUID.fromString("89D3502B-0F36-433A-8EF4-C502540A8331")
    val AMS_REMOTE_COMMAND_UUID: UUID = UUID.fromString("9B3C81D8-57B1-4A8A-B8DF-FB6568F30E19")
    val AMS_ENTITY_UPDATE_UUID: UUID = UUID.fromString("2F7DD41B-3BA5-415A-9254-14456860545E")
    val AMS_ENTITY_ATTRIBUTE_UUID: UUID = UUID.fromString("C6B5740F-281D-470A-9751-3F1A705E3776")

    // AMS Remote Command IDs
    const val REMOTE_CMD_PLAY: Byte = 0
    const val REMOTE_CMD_PAUSE: Byte = 1
    const val REMOTE_CMD_TOGGLE_PLAY_PAUSE: Byte = 2
    const val REMOTE_CMD_NEXT_TRACK: Byte = 3
    const val REMOTE_CMD_PREVIOUS_TRACK: Byte = 4
    const val REMOTE_CMD_VOLUME_UP: Byte = 5
    const val REMOTE_CMD_VOLUME_DOWN: Byte = 6

    // AMS Entity IDs
    const val ENTITY_ID_PLAYER: Byte = 0
    const val ENTITY_ID_QUEUE: Byte = 1
    const val ENTITY_ID_TRACK: Byte = 2

    // AMS Track Attributes
    const val TRACK_ATTR_ARTIST: Byte = 0
    const val TRACK_ATTR_ALBUM: Byte = 1
    const val TRACK_ATTR_TITLE: Byte = 2
    const val TRACK_ATTR_DURATION: Byte = 3
}

