# Tasks: Cleanup Build & Fix Sync

- [ ] **Phase 1: Build Script Cleanup**
    - [ ] Remove redundant NDK block from `defaultConfig` in `app/build.gradle`
    - [ ] Correctly apply 16 KB alignment flags in main `externalNativeBuild` block
    - [ ] Fix syntax/indentation in `app/build.gradle`
- [ ] **Phase 2: Validation**
    - [ ] Perform Gradle Sync
    - [ ] Run a clean build
- [ ] **Phase 3: IDE Recovery**
    - [ ] Verify "app" run configuration is available
    - [ ] Verify Play button status
