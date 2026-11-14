
/** The web server process: everything HTTP, no Telegram. */
fun main() {
    LocationStore.localityResolver = LocalityOverrides::resolve
    WebService.start(IngestConfig.PORT)
}
