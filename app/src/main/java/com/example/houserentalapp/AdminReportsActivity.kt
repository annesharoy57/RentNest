package com.example.houserentalapp

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.database.*

class AdminReportsActivity : AppCompatActivity() {

    private lateinit var rvReports: RecyclerView
    private lateinit var pbReports: ProgressBar
    private lateinit var tvNoReports: TextView
    private val reportList = mutableListOf<Report>()
    private lateinit var adapter: ReportAdapter

    private val database = FirebaseDatabase.getInstance().reference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_reports)

        rvReports = findViewById(R.id.rvAdminReports)
        pbReports = findViewById(R.id.pbReports)
        tvNoReports = findViewById(R.id.tvNoReports)

        findViewById<View>(R.id.btnBackReports).setOnClickListener { finish() }

        rvReports.layoutManager = LinearLayoutManager(this)
        adapter = ReportAdapter(reportList, 
            onDismiss = { report -> dismissReport(report) },
            onDeleteAction = { report -> showActionConfirmation(report) }
        )
        rvReports.adapter = adapter

        loadReports()
    }

    private fun loadReports() {
        pbReports.visibility = View.VISIBLE
        database.child("Reports").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                reportList.clear()
                for (data in snapshot.children) {
                    val report = data.getValue(Report::class.java)
                    if (report != null && report.status == "PENDING") {
                        report.reportId = data.key
                        reportList.add(report)
                    }
                }
                pbReports.visibility = View.GONE
                tvNoReports.visibility = if (reportList.isEmpty()) View.VISIBLE else View.GONE
                adapter.notifyDataSetChanged()
            }
            override fun onCancelled(error: DatabaseError) { 
                pbReports.visibility = View.GONE
                Log.e("AdminReports", "Read Reports Denied: ${error.message}")
            }
        })
    }

    private fun dismissReport(report: Report) {
        val id = report.reportId ?: return
        database.child("Reports").child(id).child("status").setValue("DISMISSED")
            .addOnSuccessListener { Toast.makeText(this, "Report dismissed", Toast.LENGTH_SHORT).show() }
            .addOnFailureListener { e -> Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_SHORT).show() }
    }

    private fun showActionConfirmation(report: Report) {
        val isUserReport = report.type == "USER_REPORT"
        val title = if (isUserReport) "Delete Reported User" else "Delete Reported Owner"
        val message = if (isUserReport) 
            "DEEP DELETE User '${report.targetUserName ?: "User"}'? This wipes their profile, reviews, and bookings EVERYWHERE." 
            else "DEEP DELETE Owner and all their data (Houses, Requests, Reviews) EVERYWHERE?"

        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Delete Everything") { _, _ ->
                if (isUserReport) deepDeleteUserAccount(report) else deepDeleteOwnerAccount(report)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deepDeleteOwnerAccount(report: Report) {
        val ownerId = report.ownerId ?: return
        val reportId = report.reportId ?: return
        pbReports.visibility = View.VISIBLE
        val updates = hashMapOf<String, Any?>()

        // 1. Wipe Owner's properties and reviews on them
        database.child("Properties").orderByChild("ownerId").equalTo(ownerId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    for (prop in snapshot.children) {
                        val pId = prop.key
                        updates["/Properties/$pId"] = null
                        updates["/Reviews/$pId"] = null
                    }
                    
                    // 2. Wipe profile and related owner nodes
                    updates["/Users/$ownerId"] = null
                    updates["/OwnerRequests/$ownerId"] = null
                    updates["/Revenue/$ownerId"] = null
                    updates["/Notifications/$ownerId"] = null
                    updates["/Reports/$reportId/status"] = "RESOLVED_DELETED"
                    
                    performWipe(updates)
                }
                override fun onCancelled(error: DatabaseError) { 
                    pbReports.visibility = View.GONE
                    Toast.makeText(this@AdminReportsActivity, "Permission Denied: ${error.message}", Toast.LENGTH_LONG).show()
                }
            })
    }

    private fun deepDeleteUserAccount(report: Report) {
        val userId = report.targetUserId ?: return
        val reportId = report.reportId ?: return
        pbReports.visibility = View.VISIBLE
        
        val updates = mutableMapOf<String, Any?>()
        updates["/Users/$userId"] = null
        updates["/Bookings/$userId"] = null
        updates["/UserReviews/$userId"] = null
        updates["/SavedProperties/$userId"] = null
        updates["/Favorites/$userId"] = null
        updates["/Notifications/$userId"] = null
        updates["/Reports/$reportId/status"] = "RESOLVED_DELETED"

        // Search and wipe their bookings from all Owners
        database.child("OwnerRequests").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (ownerSnapshot in snapshot.children) {
                    for (bookingSnapshot in ownerSnapshot.children) {
                        if (bookingSnapshot.child("userId").value == userId) {
                            updates["/OwnerRequests/${ownerSnapshot.key}/${bookingSnapshot.key}"] = null
                        }
                    }
                }
                
                // Search and wipe their reviews from all Properties
                database.child("Reviews").addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(revSnapshot: DataSnapshot) {
                        for (propSnapshot in revSnapshot.children) {
                            for (rev in propSnapshot.children) {
                                if (rev.child("userId").value == userId) {
                                    updates["/Reviews/${propSnapshot.key}/${rev.key}"] = null
                                }
                            }
                        }
                        performWipe(updates)
                    }
                    override fun onCancelled(error: DatabaseError) { pbReports.visibility = View.GONE }
                })
            }
            override fun onCancelled(error: DatabaseError) { 
                pbReports.visibility = View.GONE
                Toast.makeText(this@AdminReportsActivity, "Permission Denied: Check Firebase Rules", Toast.LENGTH_LONG).show()
            }
        })
    }

    private fun performWipe(updates: Map<String, Any?>) {
        database.updateChildren(updates).addOnSuccessListener {
            pbReports.visibility = View.GONE
            Toast.makeText(this, "Target account and all its history wiped successfully", Toast.LENGTH_LONG).show()
        }.addOnFailureListener { e ->
            pbReports.visibility = View.GONE
            Toast.makeText(this, "Wipe failed: ${e.message}", Toast.LENGTH_SHORT).show()
            Log.e("AdminReports", "Update failed: ${e.message}")
        }
    }
}

data class Report(
    var reportId: String? = null,
    val type: String? = "PROPERTY_REPORT", 
    val reporterId: String? = null,
    val reporterName: String? = null,
    val propertyId: String? = null,
    val ownerId: String? = null,
    val propertyTitle: String? = null,
    val propertyImage: String? = null,
    val targetUserId: String? = null,
    val targetUserName: String? = null,
    val targetUserEmail: String? = null,
    val status: String? = "PENDING",
    val timestamp: Long? = null
)

class ReportAdapter(
    private val reports: List<Report>,
    private val onDismiss: (Report) -> Unit,
    private val onDeleteAction: (Report) -> Unit
) : RecyclerView.Adapter<ReportAdapter.ReportViewHolder>() {

    class ReportViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvReportTitle)
        val tvDetails: TextView = view.findViewById(R.id.tvReportDetails)
        val ivHouse: ImageView = view.findViewById(R.id.ivReportHouseImage)
        val btnDismiss: Button = view.findViewById(R.id.btnJustifyReport)
        val btnDelete: Button = view.findViewById(R.id.btnDeleteOwner)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReportViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_admin_report, parent, false)
        return ReportViewHolder(view)
    }

    override fun onBindViewHolder(holder: ReportViewHolder, position: Int) {
        val report = reports[position]
        
        if (report.type == "USER_REPORT") {
            holder.tvTitle.text = "USER REPORT: ${report.targetUserName ?: "Unknown"}"
            holder.tvDetails.text = "Reported by Owner ${report.reporterId}\nUser ID: ${report.targetUserId}"
            holder.ivHouse.setImageResource(R.drawable.ic_person)
            holder.btnDelete.text = "Delete User"
        } else {
            holder.tvTitle.text = "HOUSE REPORT: ${report.propertyTitle ?: "Property"}"
            holder.tvDetails.text = "Reporter: ${report.reporterName ?: report.reporterId}\nOwner ID: ${report.ownerId}"
            holder.btnDelete.text = "Delete Owner"
            if (!report.propertyImage.isNullOrEmpty()) {
                Glide.with(holder.itemView.context).load(report.propertyImage).placeholder(R.drawable.ic_home).into(holder.ivHouse)
            } else {
                holder.ivHouse.setImageResource(R.drawable.ic_home)
            }
        }

        holder.btnDismiss.setOnClickListener { onDismiss(report) }
        holder.btnDelete.setOnClickListener { onDeleteAction(report) }
    }

    override fun getItemCount(): Int = reports.size
}
