@file:OptIn(ExperimentalPermissionsApi::class)

package com.example.geupjo_bus

import android.Manifest
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.example.geupjo_bus.api.BusApiClient
import com.example.geupjo_bus.api.BusArrivalItem
import com.example.geupjo_bus.api.BusStop
import com.example.geupjo_bus.ui.rememberMapViewWithLifecycle
import com.example.geupjo_bus.ui.theme.Geupjo_BusTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberMarkerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLDecoder
import java.util.concurrent.TimeUnit
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt
import androidx.work.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class) // Accompanist 경고 처리
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Geupjo_BusTheme {
                var drawerState by remember { mutableStateOf(false) }
                var currentScreen by remember { mutableStateOf("home") } // 현재 화면 상태 관리
                val scope = rememberCoroutineScope()
                val context = LocalContext.current
                Box(Modifier.fillMaxSize()) {
                    Scaffold(
                        topBar = {
                            TopAppBar(
                                title = { Text("진주시 버스 정보") },
                                actions = {
                                    IconButton(onClick = {
                                        drawerState = true
                                    }) {
                                        Text("메뉴")
                                    }
                                },
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        },
                        content = { innerPadding ->
                            // 화면 전환 로직
                            when (currentScreen) {
                                "home" -> BusAppContent(
                                    Modifier.padding(innerPadding),
                                    onSearchClick = { currentScreen = "search" }, // 검색 화면으로 전환
                                    onRouteSearchClick = { currentScreen = "route" } // 경로 검색 화면으로 전환
                                )
                                "search" -> BusStopSearchScreen(
                                    modifier = Modifier.padding(innerPadding),
                                    onBackClick = { currentScreen = "home" },
                                    apiKey = "cvmPJ15BcYEn%2FRGNukBqLTRlCXkpITZSc6bWE7tWXdBSgY%2FeN%2BvzxH%2FROLnXu%2BThzVwBc09xoXfTyckHj1IJdg%3D%3D",
                                    onBusStopClick = { busStopName ->
                                        // 버스 정류장 클릭 시 수행할 동작
                                        Log.d("MainActivity", "Selected bus stop: $busStopName")
                                    }
                                )
                                "route" -> RouteSearchScreen(
                                    modifier = Modifier.padding(innerPadding),
                                    onBackClick = { currentScreen = "home" } // 홈 화면으로 돌아가기
                                )
                                "manbok" -> ManbokScreen( // 새로 추가된 맵 화면
                                    onBackClick = { currentScreen = "home" }
                                )
                                "alarm" -> AlarmScreen( // 알람 화면으로 이동
                                    onBackClick = { currentScreen = "home" },
                                    context = context // 여기에 context를 전달
                                )
                            }
                        }
                    )

                    AnimatedVisibility(
                        visible = drawerState,
                        enter = slideInHorizontally(
                            initialOffsetX = { fullWidth -> fullWidth },
                            animationSpec = tween(500)
                        ),
                        exit = slideOutHorizontally(
                            targetOffsetX = { fullWidth -> fullWidth },
                            animationSpec = tween(500)
                        )
                    ) {
                        DrawerContent(
                            onDismiss = { drawerState = false },
                            onMenuItemClick = { screen ->
                                currentScreen = screen // 메뉴 클릭 시 화면 전환
                                drawerState = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BusAppContent(
    modifier: Modifier = Modifier,
    onSearchClick: () -> Unit,
    onRouteSearchClick: () -> Unit
) {
    val context = LocalContext.current


    var busStops by remember { mutableStateOf<List<BusStop>>(emptyList()) }
    var latitude by remember { mutableStateOf<Double?>(null) }
    var longitude by remember { mutableStateOf<Double?>(null) }

    val fusedLocationClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)
    val locationPermissionState = rememberPermissionState(permission = Manifest.permission.ACCESS_FINE_LOCATION)
    var selectedBusStop by remember { mutableStateOf<BusStop?>(null) }
    var busArrivalInfo by remember { mutableStateOf<List<BusArrivalItem>>(emptyList()) }
    var showDialog by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    val coroutineScope = rememberCoroutineScope()
    // GPS 위치 가져오기
    LaunchedEffect(Unit) {
        if (locationPermissionState.status.isGranted) {
            getCurrentLocation(context, fusedLocationClient) { lat, lng ->
                latitude = lat
                longitude = lng
                coroutineScope.launch {
                    try {
                        val encodedKey = "cvmPJ15BcYEn%2FRGNukBqLTRlCXkpITZSc6bWE7tWXdBSgY%2FeN%2BvzxH%2FROLnXu%2BThzVwBc09xoXfTyckHj1IJdg%3D%3D"
                        val apiKey = URLDecoder.decode(encodedKey, "UTF-8")

                        val response = BusApiClient.apiService.getNearbyBusStops(
                            apiKey = apiKey,
                            latitude = latitude!!,
                            longitude = longitude!!
                        )

                        if (response.isSuccessful) {
                            val responseBody = response.body()
                            busStops = responseBody?.body?.items?.itemList?.take(10) ?: emptyList() // 최대 10개 정류장
                        } else {
                            Log.e("API Error", "API 호출 실패: ${response.code()}, ${response.message()}")
                        }
                    } catch (e: Exception) {
                        Log.e("API Error", "정류장 목록 로드 실패: ${e.message}")
                    }
                }
            }
        } else {
            locationPermissionState.launchPermissionRequest()
        }
    }

    // 지도를 화면 전체에 표시
    Box(modifier = Modifier.fillMaxSize()) {
        if (latitude != null && longitude != null) {
            GoogleMapView(
                latitude = latitude!!,
                longitude = longitude!!,
                busStops = busStops, // 주변 정류장 데이터 전달
                onClick = { busStop ->
                    selectedBusStop = busStop
                    coroutineScope.launch {
                        isLoading = true // 로딩 시작
                        try {
                            val apiKey = URLDecoder.decode(
                                "cvmPJ15BcYEn%2FRGNukBqLTRlCXkpITZSc6bWE7tWXdBSgY%2FeN%2BvzxH%2FROLnXu%2BThzVwBc09xoXfTyckHj1IJdg%3D%3D",
                                "UTF-8"
                            )
                            val response = BusApiClient.apiService.getBusArrivalInfo(
                                apiKey = apiKey,
                                cityCode = 38030, // 진주시 코드
                                nodeId = busStop.nodeId!! // 선택된 정류장의 nodeId 사용
                            )
                            if (response.isSuccessful) {
                                // 도착 정보를 arrTime(예상 도착 시간) 기준으로 정렬
                                busArrivalInfo = response.body()?.body?.items?.itemList
                                    ?.sortedBy {
                                        it.arrTime ?: Int.MAX_VALUE
                                    } // null은 가장 뒤로 이동
                                    ?: emptyList()

                                if (busArrivalInfo.isEmpty()) {
                                    Log.d("Bus Info", "도착 버스 정보가 없습니다.")
                                }
                            } else {
                                Log.e(
                                    "API Error",
                                    "도착 정보 호출 실패: ${response.code()}, ${response.message()}"
                                )
                            }
                        } catch (e: Exception) {
                            Log.e("API Error", "도착 정보 로드 실패: ${e.message}")
                        } finally {
                            isLoading = false // 로딩 종료
                            showDialog = true // 다이얼로그 표시
                        }
                    }
                }
            )

            if (busStops.isEmpty()) {
                // 정류장이 없을 때 메시지 표시
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "주변에 정류장이 없습니다.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        } else {
            // 위치 정보 로드 중일 때
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("현재 위치를 확인 중입니다...", style = MaterialTheme.typography.bodyMedium)
            }
        }

        if (showDialog && selectedBusStop != null) {
            AlertDialog(
                onDismissRequest = { showDialog = false },
                title = {
                    Text(
                        text = "버스 도착 정보: ${selectedBusStop?.nodeName}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        // 지도 표시
                        val mapView = rememberMapViewWithLifecycle(context)
                        var googleMap by remember { mutableStateOf<GoogleMap?>(null) }
                        AndroidView(
                            factory = { mapView },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                        ) { map ->
                            map.getMapAsync { gMap ->
                                googleMap = gMap
                                googleMap?.clear()

                                // `nodeLati`와 `nodeLong` 값이 `String`이 아닐 수 있기 때문에, `String`으로 변환 후 `toDoubleOrNull()` 사용
                                val busStopLat = selectedBusStop?.latitude?.toString()?.toDoubleOrNull() ?: latitude ?: 0.0
                                val busStopLong = selectedBusStop?.longitude?.toString()?.toDoubleOrNull() ?: longitude ?: 0.0


                                // LatLng 객체 생성
                                val busStopLocation = LatLng(busStopLat, busStopLong)

                                // 마커 추가
                                googleMap?.addMarker(
                                    MarkerOptions()
                                        .position(busStopLocation)
                                        .title(selectedBusStop?.nodeName) // 마커에 타이틀 추가
                                )

                                // 지도 카메라를 마커가 있는 위치로 이동
                                googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(busStopLocation, 17f))
                            }
                        }

                        // 도착 버스 정보 표시
                        when {
                            busArrivalInfo.isEmpty() && !isLoading -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp)
                                ) {
                                    Text(
                                        text = "도착 버스 정보가 없습니다.",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                            isLoading -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    CircularProgressIndicator(color = MaterialTheme.colorScheme.secondary)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("버스 도착 정보를 불러오는 중입니다...")
                                }
                            }
                            else -> {
                                // 도착 버스 정보 카드 목록
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp)
                                        .verticalScroll(rememberScrollState()) // 스크롤 가능하도록 설정
                                ) {
                                    var alarmBusArrivals by remember { mutableStateOf(loadAlarms(context)) }

                                    busArrivalInfo.forEach { arrival ->
                                        val arrivalMinutes = (arrival.arrTime ?: 0) / 60
                                        val remainingStations = arrival.arrPrevStationCnt ?: 0

                                        // 알람이 설정된 상태인지 확인
                                        val isAlarmSet = alarmBusArrivals.any { it.nodeId == arrival.nodeId && it.routeNo == arrival.routeNo && it.routeId == arrival.routeId}

                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp),
                                            shape = MaterialTheme.shapes.medium,
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(16.dp)
                                            ) {
                                                Row(
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(
                                                        modifier = Modifier.weight(1f)
                                                    ) {
                                                        Text(
                                                            text = "버스 번호: ${arrival.routeNo}",
                                                            style = MaterialTheme.typography.bodyLarge,
                                                            color = MaterialTheme.colorScheme.primary
                                                        )
                                                        Spacer(modifier = Modifier.height(4.dp))
                                                        Text(
                                                            text = "예상 도착 시간: ${arrivalMinutes}분 (${remainingStations}개 정류장)",
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            color = MaterialTheme.colorScheme.onSurface
                                                        )
                                                    }

                                                    // 알람 버튼을 누르면 알람 목록에 추가/제거하고 상태를 업데이트
                                                    IconButton(onClick = {
                                                        toggleAlarm(arrival, alarmBusArrivals.toMutableList(), context).also {
                                                            // 상태 업데이트 후 UI 즉시 반영
                                                            alarmBusArrivals = loadAlarms(context)
                                                        }
                                                    }) {
                                                        Icon(
                                                            imageVector = Icons.Default.Alarm,
                                                            contentDescription = "알람 설정",
                                                            tint = if (isAlarmSet) Color.Red else MaterialTheme.colorScheme.onSurface
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showDialog = false }) {
                        Text("확인", color = MaterialTheme.colorScheme.primary)
                    }
                }
            )
        }
    }

}

// GoogleMapView 추가했음(12/5)
@Composable
fun GoogleMapView(
    latitude: Double,
    longitude: Double,
    busStops: List<BusStop>,
    onClick: (BusStop) -> Unit
) {
    val cameraPositionState = remember {
        CameraPositionState(
            position = CameraPosition.fromLatLngZoom(LatLng(latitude, longitude), 17f)
        )
    }

    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        onMapLoaded = {
            Log.d("GoogleMap", "지도 로드 완료")
        }
    ) {
        // 현재 위치 마커
        Marker(
            state = rememberMarkerState(position = LatLng(latitude, longitude)),
            title = "현재 위치",
            snippet = "여기가 현재 위치입니다."
        )
        var selectedBusStop by remember { mutableStateOf<BusStop?>(null) }
        // 정류장 마커
        busStops.forEach { busStop ->
            val lat = busStop.latitude
            val lng = busStop.longitude

            if (lat != null && lng != null) {
                Marker(
                    state = rememberMarkerState(position = LatLng(lat, lng)),
                    title = busStop.nodeName,
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE),
                    onClick = {
                        // 선택된 정류장 정보를 콜백으로 전달
                        onClick(busStop)
                        true // 클릭 이벤트 소비
                    }
                )
            }
        }
    }
}



@Composable
fun NearbyBusStop(busStopName: String, distance: String, currentlat: Double, currentlong: Double, busStoplati: Double, busStoplong: Double, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable (onClick = onClick)
            .padding(16.dp)
    ) {
        Text(text = busStopName, style = MaterialTheme.typography.titleMedium)
        Text(text = distance, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        Text(text = "${getDistance(currentlat, currentlong, busStoplati, busStoplong).toInt()} m", style = MaterialTheme.typography.bodySmall, color = Color.Gray)

    }
}

@Composable
fun BusArrivalInfo(busNumber: String, arrivalTime: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp)
    ) {
        Text(text = busNumber, style = MaterialTheme.typography.titleMedium)
        Text(text = arrivalTime, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
    }
}
@Composable
fun getDistance( currentlat: Double, currentlong: Double, busStoplati: Double, busStoplong: Double ) : Double {
    val R = 6372.8*1000
    val dLat = Math.toRadians(busStoplati - currentlat)
    val dLon = Math.toRadians(busStoplong - currentlong)
    val a = sin(dLat / 2).pow(2.0) + sin(dLon / 2).pow(2.0) * cos(Math.toRadians(currentlat)) * cos(Math.toRadians(busStoplati))
    val c = 2 * asin(sqrt(a))
    val distance = Math.round((R * c)).toDouble()
    return distance
}
// 현재 위치를 가져오는 함수
fun getCurrentLocation(
    context: android.content.Context,
    fusedLocationClient: FusedLocationProviderClient,
    onLocationRetrieved: (Double, Double) -> Unit
) {
    try {
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location: Location? ->
                if (location != null) {
                    val latitude = location.latitude
                    val longitude = location.longitude
                    Log.d("Location", "위도: $latitude, 경도: $longitude")
                    onLocationRetrieved(latitude, longitude)
                } else {
                    Log.d("Location", "위치를 가져올 수 없습니다.")
                    Toast.makeText(context, "위치를 가져올 수 없습니다.", Toast.LENGTH_SHORT).show()
                }
            }
    } catch (e: SecurityException) {
        e.printStackTrace()
    }
}

@Composable
fun DrawerContent(onDismiss: () -> Unit, onMenuItemClick: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(250.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp)
    ) {
        Text(
            text = "닫기",
            modifier = Modifier
                .fillMaxWidth() // 터치 인식 범위를 넓힘
                .clickable(onClick = onDismiss)
                .padding(12.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), shape = MaterialTheme.shapes.small),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onPrimary // 글자 색상 변경
        )

        Spacer(modifier = Modifier.height(16.dp))

        DrawerMenuItem(label = "홈", onClick = { onMenuItemClick("home") })
        DrawerMenuItem(label = "정류장 검색", onClick = { onMenuItemClick("search") })
        DrawerMenuItem(label = "경로 검색", onClick = { onMenuItemClick("route") })
        DrawerMenuItem(label = "만보기", onClick = { onMenuItemClick("manbok") })
        DrawerMenuItem(label = "알람", onClick = { onMenuItemClick("alarm") })
    }
}

@Composable
fun DrawerMenuItem(label: String, onClick: () -> Unit) {
    Row( // Row를 사용하여 터치 인식 범위 및 정렬을 개선
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp, horizontal = 12.dp)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.05f), shape = MaterialTheme.shapes.small),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary // 글자 색상 변경
        )
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManbokScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current

    // SharedPreferences에서 초기 걸음 수 불러오기
    var stepCount by remember { mutableStateOf(loadStepCount(context)) }

    val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    val stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR) // 걸음 감지 센서

    // 센서가 없다면 알림
    if (stepSensor == null) {
        Log.d("ManbokScreen", "Step sensor not available.")
    }

    // 걸음 수 감지 리스너 설정
    val stepCountListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            if (event != null && event.sensor.type == Sensor.TYPE_STEP_DETECTOR) {
                // 걸음 감지 시 카운트 증가
                if (event.values.isNotEmpty()) {
                    saveStepCount(context, loadStepCount(context) + 1) // SharedPreferences에 걸음 수 저장
                    stepCount = loadStepCount(context) // UI를 최신 데이터로 갱신
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
            // 센서의 정확도가 변경되었을 때 처리할 코드 (보통은 사용하지 않아도 됨)
        }
    }

    // 센서 리스너 등록
    LaunchedEffect(Unit) {
        if (stepSensor != null) {
            sensorManager.registerListener(
                stepCountListener,
                stepSensor,
                SensorManager.SENSOR_DELAY_UI
            )
        }
    }

    // 권한 요청 코드 추가
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACTIVITY_RECOGNITION
            ) != PackageManager.PERMISSION_GRANTED) {
            // 권한 요청
            ActivityCompat.requestPermissions(
                context as Activity,
                arrayOf(Manifest.permission.ACTIVITY_RECOGNITION),
                1
            )
        } else {
            // 권한이 이미 있다면 서비스 시작
            val stepCountServiceIntent = Intent(context, StepCountService::class.java)
            ContextCompat.startForegroundService(context, stepCountServiceIntent)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                context as Activity,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                1
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 상단 바
        TopAppBar(
            title = { Text("만보기 화면") },
            modifier = Modifier.align(Alignment.TopCenter) // 화면 상단에 배치
        )

        // 중앙에 내용 배치
        Column(
            modifier = Modifier.align(Alignment.Center), // 중앙에 배치
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 걸음 수 표시
            Text(
                text = "걸음 수: $stepCount",
                style = TextStyle(fontSize = 24.sp, color = Color.Black) // sp 단위 사용
            )
            Spacer(modifier = Modifier.height(16.dp)) // 여백 추가
            Text(
                text = "이동 거리: ${round(stepCount * 0.6)} m",
                style = TextStyle(fontSize = 20.sp, color = Color.Black)
            )
            Text(
                text = "소모 칼로리: ${round(stepCount * 0.03)} kcal",
                style = TextStyle(fontSize = 20.sp, color = Color.Black)
            )
            // 초기화 버튼 추가
            Button(
                onClick = {
                    saveStepCount(context, 0) // 걸음 수 초기화 후 SharedPreferences에 저장
                    stepCount = loadStepCount(context) // 초기화된 값으로 갱신
                },
                modifier = Modifier.padding(top = 16.dp) // 버튼 위에 여백 추가
            ) {
                Text("초기화")
            }
        }

        // 뒤로 가기 버튼을 상단 바 아래에 배치
        Button(
            onClick = onBackClick,
            modifier = Modifier
                .align(Alignment.TopEnd) // 오른쪽 상단 배치
                .padding(top = 72.dp, end = 16.dp) // 여백을 추가하여 아래로 내리기
        ) {
            Text("뒤로 가기")
        }
    }

    // 화면이 종료될 때 센서 리스너를 해제하여 메모리 누수를 방지
    DisposableEffect(Unit) {
        onDispose {
            sensorManager.unregisterListener(stepCountListener)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmScreen(onBackClick: () -> Unit, context: Context) {
    // State for alarmBusArrivals and busArrivalInfo
    var alarmBusArrivals by remember { mutableStateOf(loadAlarms(context)) }
    var busArrivalInfo by remember { mutableStateOf<List<BusArrivalItem>>(emptyList()) }

    // Coroutine scope to launch API calls and delay
    val coroutineScope = rememberCoroutineScope()

    // 10초마다 반복 실행되는 작업
    LaunchedEffect(Unit) {
        while (true) {
            // 예약된 버스 도착 작업 실행
            scheduleBusArrivalWork(context)
            // Make API requests for each arrival
            alarmBusArrivals.forEach { arrival ->
                coroutineScope.launch {
                    try {
                        val apiKey = URLDecoder.decode(
                            "cvmPJ15BcYEn%2FRGNukBqLTRlCXkpITZSc6bWE7tWXdBSgY%2FeN%2BvzxH%2FROLnXu%2BThzVwBc09xoXfTyckHj1IJdg%3D%3D",
                            "UTF-8"
                        )

                        // Make API call
                        val response = BusApiClient.apiService.getBusArrivalInfo(
                            apiKey = apiKey,
                            cityCode = 38030, // 진주시 코드
                            nodeId = arrival.nodeId!! // 선택된 정류장의 nodeId 사용
                        )

                        if (response.isSuccessful) {
                            // Process API response
                            busArrivalInfo = response.body()?.body?.items?.itemList
                                ?.sortedBy {
                                    it.arrTime ?: Int.MAX_VALUE
                                } ?: emptyList()
                        } else {
                            Log.e("API Error", "도착 정보 호출 실패: ${response.code()}, ${response.message()}")
                        }
                    } catch (e: Exception) {
                        Log.e("API Error", "도착 정보 로드 실패: ${e.message}")
                    }
                }
            }
            delay(10_000L)
        }
    }

    // 상단 바
    Box(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("알람 설정 화면") },
            modifier = Modifier.align(Alignment.TopCenter) // 상단에 배치
        )

        // 뒤로 가기 버튼
        Button(
            onClick = onBackClick,
            modifier = Modifier
                .align(Alignment.TopEnd) // 오른쪽 상단 배치
                .padding(top = 72.dp, end = 16.dp) // 여백을 추가하여 아래로 내리기
        ) {
            Text("뒤로 가기")
        }

        // 알람 설정된 버스들이 없다면 안내 메시지
        if (alarmBusArrivals.isEmpty()) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center) // 중앙에 배치
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("설정된 알람이 없습니다.", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            // 알람 설정된 버스 정보 리스트
            LazyColumn(
                modifier = Modifier.padding(top = 130.dp) // 상단바와 겹치지 않게 여백 추가
            ) {
                items(alarmBusArrivals) { arrival ->
                    busArrivalInfo.forEach { arrivals ->
                        if (arrivals.nodeId == arrival.nodeId && arrivals.routeNo == arrival.routeNo && arrivals.routeId == arrival.routeId) {
                            val arrivalMinutes = (arrivals.arrTime ?: 0) / 60
                            val remainingStations = arrivals.arrPrevStationCnt ?: 0
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                shape = MaterialTheme.shapes.medium,
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    Text(
                                        text = "${arrivals.nodeName}",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "버스 번호: ${arrivals.routeNo}",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "예상 도착 시간: ${arrivalMinutes}분 (${remainingStations}개 정류장)",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // 알람 해제 버튼
                                    Button(
                                        onClick = {
                                            // 알람 해제 처리
                                            val updatedList = alarmBusArrivals.toMutableList().apply {
                                                // 알람을 제거
                                                removeAll { it.nodeId == arrival.nodeId && it.routeNo == arrival.routeNo && it.routeId == arrival.routeId }
                                            }

                                            // 상태 업데이트 (UI 즉시 갱신)
                                            alarmBusArrivals = updatedList

                                            // SharedPreferences에도 변경된 알람 리스트 저장
                                            saveAlarms(context, updatedList)
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("알람 해제")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}




class BusArrivalWork(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {
    override fun doWork(): Result {
        val context = applicationContext
        val coroutineScope = CoroutineScope(Dispatchers.IO)

        coroutineScope.launch {
            val alarmBusArrivals = loadAlarms(context)

            alarmBusArrivals.forEach { arrival ->
                try {
                    val apiKey = URLDecoder.decode("cvmPJ15BcYEn%2FRGNukBqLTRlCXkpITZSc6bWE7tWXdBSgY%2FeN%2BvzxH%2FROLnXu%2BThzVwBc09xoXfTyckHj1IJdg%3D%3D", "UTF-8")

                    val response = withContext(Dispatchers.IO) {
                        BusApiClient.apiService.getBusArrivalInfo(
                            apiKey = apiKey,
                            cityCode = 38030, // 진주시 코드
                            nodeId = arrival.nodeId!!
                        )
                    }

                    if (response.isSuccessful) {
                        val busArrivalInfo = response.body()?.body?.items?.itemList
                            ?.sortedBy { it.arrTime ?: Int.MAX_VALUE }
                            ?: emptyList()

                        busArrivalInfo.forEach { arrivals ->
                            if (arrivals.nodeId == arrival.nodeId && arrivals.routeNo == arrival.routeNo && arrivals.routeId == arrival.routeId) {
                                val remainingStations = arrivals.arrPrevStationCnt ?: 0
                                if (remainingStations <= 5) {
                                    // 알림 표시
                                    showNotification(context, arrivals)

                                    // 알람 목록에서 해당 항목 삭제 후 저장
                                    val updatedList = alarmBusArrivals.toMutableList().apply {
                                        removeAll { it.nodeId == arrival.nodeId && it.routeNo == arrival.routeNo && it.routeId == arrival.routeId }
                                    }
                                    saveAlarms(context, updatedList)
                                }
                            }
                        }
                    } else {
                        Log.e("API Error", "Failed to fetch bus arrival info")
                    }
                } catch (e: Exception) {
                    Log.e("Worker Error", "Error during bus arrival info processing: ${e.message}")
                }
            }
        }

        return Result.success()
    }
}
fun toggleAlarm(busArrival: BusArrivalItem, alarmBusArrivals: MutableList<BusArrivalItem>, context: Context) {
    // 알람이 이미 등록된 경우 제거, 그렇지 않으면 추가
    if (alarmBusArrivals.any { it.nodeId == busArrival.nodeId && it.routeNo == busArrival.routeNo && it.routeId == busArrival.routeId}) {
        alarmBusArrivals.removeAll { it.nodeId == busArrival.nodeId && it.routeNo == busArrival.routeNo && it.routeId == busArrival.routeId }
    } else {
        alarmBusArrivals.add(busArrival)
    }
    saveAlarms(context, alarmBusArrivals)
}

fun saveAlarms(context: Context, alarms: List<BusArrivalItem>) {
    val sharedPreferences = context.getSharedPreferences("BusAppPrefs", Context.MODE_PRIVATE)
    val editor = sharedPreferences.edit()
    val json = Gson().toJson(alarms)
    editor.putString("alarmBusArrivals", json)
    editor.apply()
}

fun loadAlarms(context: Context): List<BusArrivalItem> {
    val sharedPreferences = context.getSharedPreferences("BusAppPrefs", Context.MODE_PRIVATE)
    val json = sharedPreferences.getString("alarmBusArrivals", null) ?: return emptyList()
    val type = object : TypeToken<List<BusArrivalItem>>() {}.type
    return Gson().fromJson(json, type)
}


fun scheduleBusArrivalWork(context: Context) {
    val workRequest = PeriodicWorkRequestBuilder<BusArrivalWork>(1, TimeUnit.MINUTES)  // 1분마다 작업 실행
        .setInitialDelay(0, TimeUnit.SECONDS)  // 즉시 시작
        .setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)  // 네트워크가 연결되어 있어야 작업 실행
                .setRequiresBatteryNotLow(true)  // 배터리가 충분히 있어야 작업 실행
                .build()
        )
        .build()

    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "BusArrivalWork",  // 고유한 이름 설정
        ExistingPeriodicWorkPolicy.REPLACE,  // 기존 작업을 교체
        workRequest
    )
}

fun showNotification(context: Context, arrivals: BusArrivalItem) {
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    // 알림 채널 설정
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channelId = "bus_arrival_channel"
        val channelName = "버스 도착 알림"
        val importance = NotificationManager.IMPORTANCE_HIGH

        val channel = NotificationChannel(channelId, channelName, importance)
        channel.description = "버스 도착 알림 채널"
        notificationManager.createNotificationChannel(channel)
    }

    // 알림 객체 생성
    val notification = Notification.Builder(context, "bus_arrival_channel")
        .setContentTitle("버스 도착 알림")
        .setContentText("${arrivals.routeNo}번 버스가 ${arrivals.arrPrevStationCnt}개 정류장 남았습니다.")
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setPriority(Notification.PRIORITY_HIGH)
        .build()

    // 알림 표시
    notificationManager.notify(arrivals.routeNo.hashCode(), notification)
}

fun createNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channelId = "BUS_ARRIVAL_CHANNEL"
        val channelName = "Bus Arrival Notifications"
        val channelDescription = "Channel for bus arrival notifications"
        val importance = NotificationManager.IMPORTANCE_HIGH

        val notificationChannel = NotificationChannel(channelId, channelName, importance).apply {
            description = channelDescription
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(notificationChannel)
    }
}
fun saveStepCount(context: Context, stepCount: Int) {
    val sharedPreferences = context.getSharedPreferences("step_data", Context.MODE_PRIVATE)
    val editor = sharedPreferences.edit()
    editor.putInt("step_count", stepCount)
    editor.apply()
}

fun loadStepCount(context: Context): Int {
    val sharedPreferences = context.getSharedPreferences("step_data", Context.MODE_PRIVATE)
    return sharedPreferences.getInt("step_count", 0) // 기본값 0
}

@Preview(showBackground = true)
@Composable
fun PreviewBusAppContent() {
    Geupjo_BusTheme {
        BusAppContent(onSearchClick = {}, onRouteSearchClick = {})
    }
}
@Composable
fun MapScreen(onBackClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "맵 화면",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Button(onClick = onBackClick) {
            Text("뒤로가기")
        }

        // 지도 표시나 추가적인 UI 요소들 넣기
    }
}
