package com.example.houserentalapp

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.houserentalapp.databinding.ActivityMapBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.overlay.Marker
import java.util.*

class MapActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMapBinding
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var database: DatabaseReference
    
    private var isPickingMode = false
    private var initialLat: Double = 0.0
    private var initialLng: Double = 0.0
    private var mapMarker: Marker? = null

    private var propertiesListener: ValueEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // OSM Configuration
        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this))
        
        binding = ActivityMapBinding.inflate(layoutInflater)
        setContentView(binding.root)

        database = FirebaseDatabase.getInstance().getReference("Properties")

        isPickingMode = intent.getBooleanExtra("PICK_MODE", false)
        initialLat = intent.getDoubleExtra("LAT", 0.0)
        initialLng = intent.getDoubleExtra("LNG", 0.0)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        setupMap()
        setupUI()

        if (!isPickingMode && initialLat == 0.0 && initialLng == 0.0) {
            // This is the "Explore Map" mode for users
            loadAllPropertiesOnMap()
        }
    }

    private fun setupMap() {
        binding.mapView.setTileSource(TileSourceFactory.MAPNIK)
        binding.mapView.setMultiTouchControls(true)
        binding.mapView.zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        
        val mapController = binding.mapView.controller
        mapController.setZoom(15.0)

        if (initialLat != 0.0 && initialLng != 0.0) {
            val startPoint = GeoPoint(initialLat, initialLng)
            mapController.setCenter(startPoint)
            
            if (!isPickingMode) {
                val marker = Marker(binding.mapView)
                marker.position = startPoint
                marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                marker.title = intent.getStringExtra("PROPERTY_TITLE") ?: "House Location"
                binding.mapView.overlays.add(marker)
            }
        } else {
            // Default center if no coordinates provided
            val defaultPoint = GeoPoint(23.8103, 90.4125) // Dhaka coordinates
            mapController.setCenter(defaultPoint)
            checkPermissionAndGetLocation()
        }
    }

    private fun setupUI() {
        binding.btnBackMap.setOnClickListener { finish() }

        // Search is always useful for users too
        binding.layoutSearch.visibility = View.VISIBLE
        binding.btnSearchLocation.setOnClickListener { performSearch() }
        binding.etSearchLocation.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch()
                true
            } else false
        }

        if (isPickingMode) {
            binding.tvMapTitle.text = "Select House Location"
            binding.ivMarker.visibility = View.VISIBLE
            binding.btnConfirmLocation.visibility = View.VISIBLE
            binding.btnConfirmLocation.setOnClickListener {
                confirmSelection()
            }
        } else {
            binding.tvMapTitle.text = if (initialLat != 0.0) "House Location" else "Explore Houses"
            binding.ivMarker.visibility = View.GONE
            binding.btnConfirmLocation.visibility = View.GONE
        }

        binding.fabMyLocation.setOnClickListener {
            checkPermissionAndGetLocation()
        }
    }

    private fun loadAllPropertiesOnMap() {
        propertiesListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // Clear existing house markers if any (except my location or specific marker)
                binding.mapView.overlays.clear()
                
                for (data in snapshot.children) {
                    val property = data.getValue(Property::class.java)
                    if (property != null && property.isAvailable && 
                        property.latitude != null && property.longitude != null &&
                        property.latitude != 0.0 && property.longitude != 0.0) {
                        
                        val geoPoint = GeoPoint(property.latitude!!, property.longitude!!)
                        val marker = Marker(binding.mapView)
                        marker.position = geoPoint
                        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        marker.title = property.title
                        marker.snippet = "Rent: ৳${property.rentAmount}"
                        
                        marker.setOnMarkerClickListener { m, _ ->
                            val intent = Intent(this@MapActivity, PropertyDetailsActivity::class.java)
                            intent.putExtra("PROPERTY_ID", property.propertyId)
                            startActivity(intent)
                            true
                        }
                        
                        binding.mapView.overlays.add(marker)
                    }
                }
                binding.mapView.invalidate() // Refresh map
            }

            override fun onCancelled(error: DatabaseError) {
                if (FirebaseAuth.getInstance().currentUser != null) {
                    Toast.makeText(this@MapActivity, "Failed to load properties", Toast.LENGTH_SHORT).show()
                }
            }
        }
        database.addValueEventListener(propertiesListener!!)
    }

    private fun performSearch() {
        val query = binding.etSearchLocation.text.toString().trim()
        if (query.isEmpty()) return

        try {
            val geocoder = Geocoder(this, Locale.getDefault())
            val addresses: List<Address>? = geocoder.getFromLocationName(query, 1)

            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                val targetPoint = GeoPoint(address.latitude, address.longitude)
                binding.mapView.controller.animateTo(targetPoint)
                binding.mapView.controller.setZoom(15.0)
            } else {
                Toast.makeText(this, "Location not found", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Search error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkPermissionAndGetLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    val currentPoint = GeoPoint(location.latitude, location.longitude)
                    binding.mapView.controller.animateTo(currentPoint)
                    binding.mapView.controller.setZoom(15.0)
                }
            }
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1001)
        }
    }

    private fun confirmSelection() {
        val center = binding.mapView.mapCenter as GeoPoint
        val address = getAddressFromLatLng(center.latitude, center.longitude)
        
        val resultIntent = Intent()
        resultIntent.putExtra("LAT", center.latitude)
        resultIntent.putExtra("LNG", center.longitude)
        resultIntent.putExtra("ADDRESS", address)
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }

    private fun getAddressFromLatLng(lat: Double, lng: Double): String {
        return try {
            val geocoder = Geocoder(this, Locale.getDefault())
            val addresses = geocoder.getFromLocation(lat, lng, 1)
            if (!addresses.isNullOrEmpty()) {
                addresses[0].getAddressLine(0) ?: "Unknown Location"
            } else {
                "Selected Location"
            }
        } catch (e: Exception) {
            "Selected Location"
        }
    }

    @SuppressLint("MissingPermission")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            checkPermissionAndGetLocation()
        }
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
    }

    override fun onDestroy() {
        propertiesListener?.let { database.removeEventListener(it) }
        binding.mapView.onDetach()
        super.onDestroy()
    }
}
