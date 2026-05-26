package com.example.houserentalapp

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class UserBookingsActivity : AppCompatActivity() {

    private lateinit var rvBookings: RecyclerView
    private lateinit var pbBookings: ProgressBar
    private lateinit var tvNoBookings: TextView
    private val bookingList = mutableListOf<Booking>()
    private lateinit var adapter: BookingAdapter

    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance().getReference("Bookings")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_bookings)

        rvBookings = findViewById(R.id.rvUserBookings)
        pbBookings = findViewById(R.id.pbBookings)
        tvNoBookings = findViewById(R.id.tvNoBookings)

        findViewById<View>(R.id.btnBackBookings).setOnClickListener { finish() }

        rvBookings.layoutManager = LinearLayoutManager(this)
        adapter = BookingAdapter(bookingList)
        rvBookings.adapter = adapter

        loadBookings()
    }

    private fun loadBookings() {
        val userId = auth.currentUser?.uid ?: return
        pbBookings.visibility = View.VISIBLE

        database.child(userId).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                bookingList.clear()
                for (data in snapshot.children) {
                    val booking = data.getValue(Booking::class.java)
                    if (booking != null) {
                        bookingList.add(booking)
                    }
                }
                pbBookings.visibility = View.GONE
                tvNoBookings.visibility = if (bookingList.isEmpty()) View.VISIBLE else View.GONE
                adapter.notifyDataSetChanged()
            }

            override fun onCancelled(error: DatabaseError) {
                pbBookings.visibility = View.GONE
                Toast.makeText(this@UserBookingsActivity, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }
}

class BookingAdapter(private val bookingList: List<Booking>) :
    RecyclerView.Adapter<BookingAdapter.BookingViewHolder>() {

    class BookingViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivProperty: ImageView = view.findViewById(R.id.ivBookingProperty)
        val tvTitle: TextView = view.findViewById(R.id.tvBookingTitle)
        val tvLocation: TextView = view.findViewById(R.id.tvBookingLocation)
        val tvPrice: TextView = view.findViewById(R.id.tvBookingPrice)
        val tvStatus: TextView = view.findViewById(R.id.tvBookingStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookingViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_booking, parent, false)
        return BookingViewHolder(view)
    }

    override fun onBindViewHolder(holder: BookingViewHolder, position: Int) {
        val booking = bookingList[position]
        val context = holder.itemView.context
        
        holder.tvTitle.text = booking.propertyTitle
        holder.tvLocation.text = booking.propertyLocation
        holder.tvPrice.text = "৳ ${booking.propertyPrice}"
        holder.tvStatus.text = booking.status

        // Dynamic Status Colors
        when (booking.status) {
            "ACCEPTED" -> {
                holder.tvStatus.background.setTint(Color.parseColor("#4CAF50")) // Green
                holder.tvStatus.text = "ACCEPTED"
            }
            "DECLINED" -> {
                holder.tvStatus.background.setTint(Color.parseColor("#F44336")) // Red
                holder.tvStatus.text = "DECLINED"
            }
            else -> {
                holder.tvStatus.background.setTint(Color.parseColor("#FFB300")) // Orange/Yellow
                holder.tvStatus.text = "PENDING"
            }
        }

        if (!booking.propertyImage.isNullOrEmpty()) {
            Glide.with(context).load(booking.propertyImage).placeholder(R.drawable.ic_home).into(holder.ivProperty)
        }
    }

    override fun getItemCount(): Int = bookingList.size
}
