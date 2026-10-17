package com.lsucu.pods

/** Внешний вид — по нему выбираем рисунок / PNG (img_<key>_pods, img_<key>_case). */
enum class Family(val key: String) {
    AIRPODS_1("airpods12"),
    AIRPODS_3("airpods3"),
    AIRPODS_4("airpods4"),
    PRO("pro"),
    PRO3("pro3"),
    MAX("max");

    val hasCase get() = this != MAX
}

/**
 * Модели по id из BLE-рекламы и что они умеют по каналу управления.
 * Id и возможности — по LibrePods / furiousmac; на живом железе проверена только Pro 3.
 */
enum class PodsModel(
    val id: Int,
    val title: String,
    val family: Family,
    val anc: Boolean = false,          // шумодав + прозрачность + выкл
    val adaptive: Boolean = false,     // адаптивный режим и его сила
    val conversation: Boolean = false, // адаптация к разговору
    val stem: Boolean = false,         // нажатия на ножку (перехват жестов)
) {
    AIRPODS_1(0x2002, "AirPods", Family.AIRPODS_1),
    AIRPODS_2(0x200F, "AirPods (2nd generation)", Family.AIRPODS_1),
    AIRPODS_3(0x2013, "AirPods (3rd generation)", Family.AIRPODS_3, stem = true),
    AIRPODS_4(0x2019, "AirPods 4", Family.AIRPODS_4, stem = true),
    AIRPODS_4_ANC(0x201B, "AirPods 4", Family.AIRPODS_4,
        anc = true, adaptive = true, conversation = true, stem = true),
    PRO(0x200E, "AirPods Pro", Family.PRO, anc = true, stem = true),
    PRO_2(0x2014, "AirPods Pro (2nd generation)", Family.PRO,
        anc = true, adaptive = true, conversation = true, stem = true),
    PRO_2_USBC(0x2024, "AirPods Pro (2nd generation)", Family.PRO,
        anc = true, adaptive = true, conversation = true, stem = true),
    PRO_3(0x2027, "AirPods Pro 3", Family.PRO3,
        anc = true, adaptive = true, conversation = true, stem = true),
    MAX(0x200A, "AirPods Max", Family.MAX, anc = true),
    MAX_USBC(0x201F, "AirPods Max", Family.MAX, anc = true),
    // модель ещё не знаем — показываем всё, наушники сами проигнорируют лишнее
    UNKNOWN(0, "AirPods", Family.PRO3, anc = true, adaptive = true, conversation = true, stem = true);

    companion object {
        fun of(id: Int): PodsModel = entries.firstOrNull { it.id == id && it != UNKNOWN } ?: UNKNOWN

        /** Для чужих спаренных устройств знаем только имя. */
        fun familyByName(name: String): Family = when {
            name.contains("Max", true) -> Family.MAX
            name.contains("Pro", true) -> Family.PRO
            else -> Family.AIRPODS_4
        }
    }
}
