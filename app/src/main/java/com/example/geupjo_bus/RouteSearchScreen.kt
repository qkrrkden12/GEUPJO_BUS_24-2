package com.example.geupjo_bus

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.geupjo_bus.ui.rememberMapViewWithLifecycle
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.ExperimentalComposeUiApi


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteSearchScreen(
    modifier: Modifier = Modifier,
    onBackClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    var departure by remember { mutableStateOf(TextFieldValue("")) }
    var destination by remember { mutableStateOf(TextFieldValue("")) }
    var selectedMode by remember { mutableStateOf("transit") }
    val modeOptions = listOf("transit" to "대중교통", "driving" to "자동차", "walking" to "도보", "bicycling" to "자전거")

    var routeResults by remember { mutableStateOf(listOf<String>()) }
    var travelTime by remember { mutableStateOf("") }
    var polylinePoints by remember { mutableStateOf<List<LatLng>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var currentLocation by remember { mutableStateOf<Location?>(null) }
    val departureMarker = remember { mutableStateOf<Marker?>(null) }
    val destinationMarker = remember { mutableStateOf<Marker?>(null) }
    val currentPolyline = remember { mutableStateOf<Polyline?>(null) }
    val recentSearches = remember {
        mutableStateListOf<Pair<String, String>>().apply {
            addAll(loadRecentSearches(context))
        }
    }

    val polylineColor = 0xFF6200EE.toInt()
    val mapView = rememberMapViewWithLifecycle(context)
    var googleMap by remember { mutableStateOf<GoogleMap?>(null) }

    LaunchedEffect(Unit) {
        currentLocation = getCurrentLocation(context)
        currentLocation?.let {
            val address = getAddressFromLocation(context, it.latitude, it.longitude)
            departure = TextFieldValue(address)
        }
        mapView.getMapAsync { gMap ->
            googleMap = gMap
            googleMap?.let { map ->
                currentLocation?.let {
                    val currentLatLng = LatLng(it.latitude, it.longitude)
                    map.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 14f))
                    departureMarker.value = map.addMarker(
                        MarkerOptions().position(currentLatLng).title("출발지")
                    )
                }
            }
        }
    }

    fun performSearch() {
        if (departure.text.isBlank() || destination.text.isBlank()) {
            Toast.makeText(context, "출발지와 도착지를 모두 입력하세요", Toast.LENGTH_SHORT).show()
            return
        }
        isSearching = true
        keyboardController?.hide()
        coroutineScope.launch {
            val (results, polyline, duration) = fetchDirections(departure.text, destination.text, selectedMode)
            routeResults = results
            travelTime = duration
            recentSearches.removeAll { it.first == departure.text && it.second == destination.text }
            recentSearches.add(0, departure.text to destination.text)
            if (recentSearches.size > 5) recentSearches.removeLast()
            saveRecentSearches(context, recentSearches)
            updateMapWithDirections(
                googleMap = googleMap,
                polyline = polyline,
                polylineColor = polylineColor,
                currentPolyline = currentPolyline,
                destinationMarker = destinationMarker,
                departureMarker = departureMarker,
                currentLocation = currentLocation
            )
            isSearching = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Button(onClick = onBackClick, modifier = Modifier.align(Alignment.Start)) {
            Text("뒤로 가기")
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = departure,
            onValueChange = { departure = it },
            label = { Text("출발지를 입력하세요") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            trailingIcon = {
                if (departure.text.isNotEmpty()) {
                    IconButton(onClick = { departure = TextFieldValue("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            },
            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Next)
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = destination,
            onValueChange = { destination = it },
            label = { Text("도착지를 입력하세요") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            trailingIcon = {
                if (destination.text.isNotEmpty()) {
                    IconButton(onClick = { destination = TextFieldValue("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            },
            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { performSearch() })
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Button(onClick = {
                val temp = departure
                departure = destination
                destination = temp
            }) {
                Icon(Icons.Default.SwapVert, contentDescription = "출발/도착 스왑")
                Spacer(modifier = Modifier.width(8.dp))
                Text("스왑")
            }

            Button(onClick = { performSearch() }) {
                Text("경로 검색")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            modeOptions.forEach { (value, label) ->
                FilterChip(
                    selected = selectedMode == value,
                    onClick = { selectedMode = value },
                    label = { Text(label) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isSearching) CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))

        Spacer(modifier = Modifier.height(16.dp))

        if (routeResults.isNotEmpty()) {
            Text("검색 결과:", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            if (travelTime.isNotEmpty()) Text("총 이동시간: $travelTime", style = MaterialTheme.typography.bodyMedium)
            routeResults.forEach {
                RouteSearchResultItem(it)
                Spacer(modifier = Modifier.height(8.dp))
            }
            AndroidView(factory = { mapView }, modifier = Modifier.fillMaxWidth().height(400.dp)) {
                it.getMapAsync { gMap -> googleMap = gMap; gMap.clear() }
            }
        } else if (!isSearching) {
            Text("검색 결과가 없습니다.", style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (recentSearches.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("최근 검색:", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = {
                    recentSearches.clear()
                    saveRecentSearches(context, emptyList())
                }) {
                    Text("모두 지우기")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            recentSearches.take(5).forEach { (from, to) ->
                Button(
                    onClick = {
                        departure = TextFieldValue(from)
                        destination = TextFieldValue(to)
                        performSearch()
                    },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Text("$from → $to")
                }
            }
        }
    }
}


fun saveRecentSearches(context: Context, searches: List<Pair<String, String>>) {
    val sharedPreferences = context.getSharedPreferences("recent_searches", Context.MODE_PRIVATE)
    val editor = sharedPreferences.edit()
    val encoded = searches.joinToString("|") { "${it.first}::${it.second}" }
    editor.putString("recent_searches", encoded)
    editor.apply()
}

fun loadRecentSearches(context: Context): List<Pair<String, String>> {
    val sharedPreferences = context.getSharedPreferences("recent_searches", Context.MODE_PRIVATE)
    val encoded = sharedPreferences.getString("recent_searches", null) ?: return emptyList()
    return encoded.split("|").mapNotNull {
        val parts = it.split("::")
        if (parts.size == 2) parts[0] to parts[1] else null
    }
}


fun updateMapWithDirections(
    googleMap: GoogleMap?,
    polyline: String?,
    polylineColor: Int,
    currentPolyline: MutableState<Polyline?>,
    destinationMarker: MutableState<Marker?>,
    departureMarker: MutableState<Marker?>,
    currentLocation: Location?
) {
    googleMap?.let { map ->
        currentPolyline.value?.remove()
        currentLocation?.let {
            val currentLatLng = LatLng(it.latitude, it.longitude)
            departureMarker.value?.remove()
            departureMarker.value = map.addMarker(
                MarkerOptions()
                    .position(currentLatLng)
                    .title("출발지")
            )
        }
        if (!polyline.isNullOrEmpty()) {
            val points = decodePolyline(polyline)
            currentPolyline.value = map.addPolyline(
                PolylineOptions()
                    .addAll(points)
                    .color(polylineColor)
                    .width(10f)
            )
            val lastPoint = points.lastOrNull()
            lastPoint?.let {
                destinationMarker.value?.remove()
                destinationMarker.value = map.addMarker(
                    MarkerOptions()
                        .position(it)
                        .title("도착지")
                )
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(it, 16f))
            }
        }
    }
}

suspend fun fetchDirections(departure: String, destination: String, mode: String): Triple<List<String>, String?, String> {
    return withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient()
            val encodedOrigin = URLEncoder.encode(departure, "UTF-8")
            val encodedDestination = URLEncoder.encode(destination, "UTF-8")
            val url = "https://maps.googleapis.com/maps/api/directions/json?" +
                    "origin=$encodedOrigin&destination=$encodedDestination&mode=$mode&language=ko&key=AIzaSyA-XxR0OPZoPTA9-TxDyqQVqaRt9EOa-Eg"

            Log.d("Directions API", "URL: $url")

            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val jsonData = response.body?.string()

            Log.d("Directions API", "Response: $jsonData")

            val routeList = mutableListOf<String>()
            var polyline: String? = null
            var totalDuration = ""

            if (jsonData != null) {
                val jsonObject = JSONObject(jsonData)
                val routes = jsonObject.getJSONArray("routes")

                if (routes.length() > 0) {
                    val route = routes.getJSONObject(0)
                    polyline = route.getJSONObject("overview_polyline").getString("points")

                    val legs = route.getJSONArray("legs")
                    totalDuration = legs.getJSONObject(0).getJSONObject("duration").getString("text")

                    val steps = legs.getJSONObject(0).getJSONArray("steps")

                    for (i in 0 until steps.length()) {
                        val step = steps.getJSONObject(i)
                        val instruction = step.getString("html_instructions")
                        val distance = step.getJSONObject("distance").getString("text")
                        val travelMode = step.getString("travel_mode")

                        val cleanInstruction = instruction.replace(Regex("<.*?>"), "")

                        if (step.has("transit_details")) {
                            val transitDetails = step.getJSONObject("transit_details")
                            val line = transitDetails.getJSONObject("line")
                            val busNumber = line.optString("short_name", "버스")
                            val departureStop = transitDetails.getJSONObject("departure_stop").getString("name")
                            val arrivalStop = transitDetails.getJSONObject("arrival_stop").getString("name")
                            val numStops = transitDetails.getInt("num_stops")
                            val congestionLevel = line.optString("short_name").lastOrNull()?.digitToIntOrNull()?.let {
                                when {
                                    it <= 3 -> "혼잡도: 낮음 🟢"
                                    it in 4..6 -> "혼잡도: 보통 🟡"
                                    else -> "혼잡도: 높음 🔴"
                                }
                            } ?: "혼잡도 정보 없음"

                            routeList.add(
                                "$cleanInstruction\n" +
                                        "- 거리: $distance\n" +
                                        "- 버스: $busNumber\n" +
                                        "- 출발 정류장: $departureStop\n" +
                                        "- 도착 정류장: $arrivalStop\n" +
                                        "- 정차 정류장 수: $numStops\n" +
                                        "- $congestionLevel"
                            )
                        } else {
                            routeList.add("$cleanInstruction\n- 거리: $distance\n- 이동 수단: $travelMode")
                        }
                    }
                }
            }

            Triple(routeList, polyline, totalDuration)
        } catch (e: Exception) {
            Log.e("Google Directions API", "Error fetching directions: ${e.message}")
            Triple(emptyList(), null, "")
        }
    }
}


fun decodePolyline(encoded: String): List<LatLng> {
    val poly = ArrayList<LatLng>()
    var index = 0
    val len = encoded.length
    var lat = 0
    var lng = 0

    while (index < len) {
        var b: Int
        var shift = 0
        var result = 0
        do {
            b = encoded[index++].code - 63
            result = result or ((b and 0x1f) shl shift)
            shift += 5
        } while (b >= 0x20)
        val dlat = if ((result and 1) != 0) (result shr 1).inv() else result shr 1
        lat += dlat

        shift = 0
        result = 0
        do {
            b = encoded[index++].code - 63
            result = result or ((b and 0x1f) shl shift)
            shift += 5
        } while (b >= 0x20)
        val dlng = if ((result and 1) != 0) (result shr 1).inv() else result shr 1
        lng += dlng

        val p = LatLng(
            lat.toDouble() / 1E5,
            lng.toDouble() / 1E5
        )
        poly.add(p)
    }
    return poly
}

@SuppressLint("MissingPermission")
suspend fun getCurrentLocation(context: Context): Location? {
    val fusedLocationProviderClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    return suspendCoroutine { continuation ->
        fusedLocationProviderClient.lastLocation
            .addOnSuccessListener { location -> continuation.resume(location) }
            .addOnFailureListener { exception -> continuation.resumeWithException(exception) }
    }
}

fun getAddressFromLocation(context: Context, latitude: Double, longitude: Double): String {
    val geocoder = Geocoder(context)
    val addresses = geocoder.getFromLocation(latitude, longitude, 1)
    return addresses?.firstOrNull()?.getAddressLine(0) ?: "주소를 찾을 수 없습니다."
}

@Composable
fun RouteSearchResultItem(route: String) {
    Text(
        text = route,
        style = MaterialTheme.typography.bodyMedium
    )
}