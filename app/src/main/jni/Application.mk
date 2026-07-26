APP_ABI := all
APP_PLATFORM := android-14
# 16 KB page size support (Android 15+ / Google Play requirement)
APP_SUPPORT_FLEXIBLE_PAGE_SIZES := true
APP_LDFLAGS += -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384
