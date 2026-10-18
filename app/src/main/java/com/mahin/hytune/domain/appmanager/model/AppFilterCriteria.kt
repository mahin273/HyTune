package com.mahin.hytune.domain.appmanager.model

enum class AppTypeFilter(val displayName: String) {
    ALL("All"),
    USER("User"),
    SYSTEM("System"),
    DISABLED("Disabled"),
    GAMES("Games")
}

enum class AppSortOrder(val displayName: String) {
    NAME_ASC("Name (A-Z)"),
    NAME_DESC("Name (Z-A)"),
    PACKAGE_ASC("Package"),
    INSTALL_TIME_DESC("Install Date"),
    UPDATE_TIME_DESC("Update Date"),
    TARGET_SDK_DESC("Target SDK")
}
