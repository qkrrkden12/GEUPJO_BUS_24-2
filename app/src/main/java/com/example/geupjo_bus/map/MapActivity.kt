package com.example.geupjo_bus.map

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.example.geupjo_bus.R
import com.google.android.material.bottomsheet.BottomSheetBehavior
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.content.Intent
import android.net.Uri
import com.example.geupjo_bus.api.BusApiClient
import com.example.geupjo_bus.ui.BusStop
import com.google.android.gms.maps.model.LatLng

class MapActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var map: GoogleMap
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<*>
    private var busStopName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)

        // Intent에서 정류장 이름 가져오기
        busStopName = intent.getStringExtra("BUS_STOP_NAME")

        // 지도 프래그먼트 초기화
        val mapFragment = supportFragmentManager.findFragmentById(R.id.mapFragment) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // BottomSheet 초기화
        val bottomSheet = findViewById<LinearLayout>(R.id.bottomSheet)
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)

        // BottomSheet 내용 설정
        val busStopNameTextView = findViewById<TextView>(R.id.busStopNameTextView)
        busStopNameTextView.text = busStopName ?: "알 수 없는 정류장"

        // 경로 안내 버튼
        val directionButton = findViewById<Button>(R.id.directionButton)
        directionButton.setOnClickListener {
            // 경로 안내 로직 추가 (예: Intent로 외부 네비게이션 앱 호출)
            startDirections(busStopName)
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap

        // 기존 마커 제거
        map.clear()

        // 지도 초기화
        val initialLocation = LatLng(37.5665, 126.9780)
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(initialLocation, 15f))

        // 불필요한 UI 비활성화
        map.uiSettings.isZoomControlsEnabled = true // 줌 버튼 활성화
        map.uiSettings.isCompassEnabled = false // 나침반 비활성화
        map.uiSettings.isMapToolbarEnabled = false // 맵 툴바 비활성화
    }


    private fun startDirections(busStopName: String?) {
        // 실제 경로 안내 로직 (외부 지도 앱 호출 예제)
        busStopName?.let {
            val gmmIntentUri = Uri.parse("google.navigation:q=$it")
            val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
            mapIntent.setPackage("com.google.android.apps.maps")
            startActivity(mapIntent)
        }
    }

    // BusApiClient를 사용해 주변 정류장 목록 가져오기
    suspend fun fetchNearbyBusStops(
        latitude: Double,
        longitude: Double,
        apiKey: String
    ): List<BusStop> {
        return try {
            val response = BusApiClient.apiService.getNearbyBusStops(
                apiKey = apiKey,
                latitude = latitude,
                longitude = longitude
            )

            if (response.isSuccessful) {
                response.body()?.body?.items?.itemList?.map {
                    BusStop(
                        name = it.nodeName ?: "알 수 없음",
                        location = LatLng(it.latitude ?: 0.0, it.longitude ?: 0.0),
                        info = "거리: ${it.nodeNumber}m"
                    )
                } ?: emptyList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

}
