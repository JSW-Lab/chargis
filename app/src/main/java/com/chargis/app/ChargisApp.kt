package com.chargis.app

import android.app.Application

/**
 * 앱 전체를 대표하는 객체. 앱이 켜질 때 화면보다 먼저 한 번 만들어진다.
 * 여기서 부품 조립대([AppContainer])를 만들어 두고, 화면은 필요할 때 꺼내 쓴다.
 */
class ChargisApp : Application() {
    // 처음 꺼낼 때 만든다. Gleo AI가 꺼져 있는 앱을 깨워 부를 때(7초 안에 답해야 함)는 화면이 없어 부품이 필요 없으므로,
    // 차량 SDK 연결 같은 무거운 준비를 건너뛰고 바로 답할 수 있다.
    val container: AppContainer by lazy { AppContainer(this) }
}
