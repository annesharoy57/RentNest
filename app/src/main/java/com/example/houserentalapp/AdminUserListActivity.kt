package com.example.houserentalapp

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.database.*

class AdminUserListActivity : AppCompatActivity() {

    private lateinit var rvUserList: RecyclerView
    private lateinit var pbUserList: ProgressBar
    private lateinit var tvTitle: TextView
    private val userList = mutableListOf<UserItem>()
    private lateinit var adapter: AdminUserAdapter

    private val database = FirebaseDatabase.getInstance().reference
    private var roleToDisplay = "User"
    private var isOwnerReporting = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_user_list)

        roleToDisplay = intent.getStringExtra("ROLE") ?: "User"
        isOwnerReporting = intent.getBooleanExtra("IS_OWNER_REPORTING", false)

        rvUserList = findViewById(R.id.rvAdminUserList)
        pbUserList = findViewById(R.id.pbUserList)
        tvTitle = findViewById(R.id.tvTitle)

        if (isOwnerReporting) {
            tvTitle.text = "Report Users"
        } else {
            tvTitle.text = if (roleToDisplay == "User") "User Directory" else "Owner Directory"
        }

        findViewById<View>(R.id.btnBackUserList).setOnClickListener { finish() }

        rvUserList.layoutManager = LinearLayoutManager(this)
        adapter = AdminUserAdapter(userList, isOwnerReporting) { user ->
            if (isOwnerReporting) {
                showReportUserDialog(user)
            } else {
                showDeleteConfirmation(user)
            }
        }
        rvUserList.adapter = adapter

        loadUsers()
    }

    private fun loadUsers() {
        pbUserList.visibility = View.VISIBLE
        // Owners report "Users", Admin views whatever role was requested
        val targetRole = if (isOwnerReporting) "User" else roleToDisplay
        
        database.child("Users").orderByChild("role").equalTo(targetRole)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    userList.clear()
                    for (data in snapshot.children) {
                        val user = data.getValue(UserItem::class.java)
                        if (user != null) {
                            user.uid = data.key
                            userList.add(user)
                        }
                    }
                    pbUserList.visibility = View.GONE
                    adapter.notifyDataSetChanged()
                }

                override fun onCancelled(error: DatabaseError) {
                    pbUserList.visibility = View.GONE
                }
            })
    }

    private fun showReportUserDialog(user: UserItem) {
        AlertDialog.Builder(this)
            .setTitle("Report User")
            .setMessage("Are you sure you want to report ${user.name} to the Admin?")
            .setPositiveButton("Report") { _, _ -> reportUserToAdmin(user) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun reportUserToAdmin(user: UserItem) {
        val currentOwnerId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return
        val reportRef = database.child("Reports").push()
        val reportId = reportRef.key ?: return
        
        val reportData = hashMapOf(
            "reportId" to reportId,
            "type" to "USER_REPORT",
            "reporterId" to currentOwnerId,
            "targetUserId" to user.uid,
            "targetUserName" to user.name,
            "targetUserEmail" to user.email,
            "status" to "PENDING",
            "timestamp" to ServerValue.TIMESTAMP
        )
        
        reportRef.setValue(reportData).addOnSuccessListener {
            Toast.makeText(this, "User reported to Admin", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showDeleteConfirmation(user: UserItem) {
        val message = if (user.role == "Owner") {
            "Deep Delete Owner ${user.name}? This will wipe their profile, houses, bookings, and reviews forever."
        } else {
            "Deep Delete user ${user.name}? This wipes their bookings, reviews, and profile everywhere."
        }

        AlertDialog.Builder(this)
            .setTitle("Confirm Deep Deletion")
            .setPositiveButton("Delete Everything") { _, _ -> deepDeleteUser(user) }
            .setNegativeButton("Cancel", null)
            .setMessage(message)
            .show()
    }

    private fun deepDeleteUser(user: UserItem) {
        val uid = user.uid ?: return
        pbUserList.visibility = View.VISIBLE
        val updates = mutableMapOf<String, Any?>()

        if (user.role == "Owner") {
            // Owner deletion (Already fairly deep, let's ensure reviews on their properties are gone)
            database.child("Properties").orderByChild("ownerId").equalTo(uid)
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        for (prop in snapshot.children) {
                            val pId = prop.key
                            updates["/Properties/$pId"] = null
                            updates["/Reviews/$pId"] = null
                        }
                        updates["/OwnerRequests/$uid"] = null
                        updates["/Revenue/$uid"] = null
                        updates["/Notifications/$uid"] = null
                        updates["/Users/$uid"] = null
                        performUpdate(updates, "Owner and all data deleted")
                    }
                    override fun onCancelled(error: DatabaseError) { pbUserList.visibility = View.GONE }
                })
        } else {
            // DEEP DELETE USER: Wiping their footprint from Owner nodes too
            // 1. Delete user profile and personal nodes
            updates["/Bookings/$uid"] = null
            updates["/UserReviews/$uid"] = null
            updates["/SavedProperties/$uid"] = null
            updates["/Favorites/$uid"] = null
            updates["/Notifications/$uid"] = null
            updates["/Users/$uid"] = null

            // 2. We need to find all bookings they made to owners and delete them there too
            database.child("OwnerRequests").addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    for (ownerNode in snapshot.children) {
                        for (booking in ownerNode.children) {
                            if (booking.child("userId").value == uid) {
                                updates["/OwnerRequests/${ownerNode.key}/${booking.key}"] = null
                            }
                        }
                    }
                    
                    // 3. Find and delete all reviews this user wrote for ANY property
                    database.child("Reviews").addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(revSnapshot: DataSnapshot) {
                            for (propNode in revSnapshot.children) {
                                for (review in propNode.children) {
                                    if (review.child("userId").value == uid) {
                                        updates["/Reviews/${propNode.key}/${review.key}"] = null
                                    }
                                }
                            }
                            performUpdate(updates, "User and all history wiped")
                        }
                        override fun onCancelled(error: DatabaseError) { performUpdate(updates, "User wiped partially") }
                    })
                }
                override fun onCancelled(error: DatabaseError) { performUpdate(updates, "User wiped partially") }
            })
        }
    }

    private fun performUpdate(updates: Map<String, Any?>, successMsg: String) {
        database.updateChildren(updates).addOnSuccessListener {
            pbUserList.visibility = View.GONE
            Toast.makeText(this, successMsg, Toast.LENGTH_SHORT).show()
        }.addOnFailureListener { e ->
            pbUserList.visibility = View.GONE
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}

data class UserItem(
    var uid: String? = null,
    val name: String? = null,
    val email: String? = null,
    val role: String? = null,
    val profileImageUrl: String? = null,
    val phone: String? = null,
    val address: String? = null,
    val profession: String? = null,
    val bio: String? = null
)

class AdminUserAdapter(
    private val users: List<UserItem>,
    private val isReporting: Boolean,
    private val onActionClick: (UserItem) -> Unit
) : RecyclerView.Adapter<AdminUserAdapter.UserViewHolder>() {

    class UserViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivUser: ImageView = view.findViewById(R.id.ivUserImage)
        val tvName: TextView = view.findViewById(R.id.tvUserName)
        val tvEmail: TextView = view.findViewById(R.id.tvUserEmail)
        val tvUserId: TextView = view.findViewById(R.id.tvUserId)
        val tvRoleDisplay: TextView = view.findViewById(R.id.tvUserRoleDisplay)
        val tvPhone: TextView = view.findViewById(R.id.tvUserPhone)
        val tvAddress: TextView = view.findViewById(R.id.tvUserAddress)
        val tvProfession: TextView = view.findViewById(R.id.tvUserProfession)
        val tvBio: TextView = view.findViewById(R.id.tvUserBio)
        val layoutExtra: LinearLayout = view.findViewById(R.id.layoutExtraInfo)
        val btnAction: ImageButton = view.findViewById(R.id.btnDeleteUser)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_admin_user, parent, false)
        return UserViewHolder(view)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        val user = users[position]
        holder.tvName.text = user.name ?: "No Name"
        holder.tvEmail.text = user.email ?: "No Email"
        holder.tvUserId.text = "ID: ${user.uid ?: "N/A"}"
        holder.tvRoleDisplay.text = user.role?.uppercase() ?: "USER"
        
        if (isReporting) {
            holder.btnAction.setImageResource(R.drawable.ic_notifications)
            holder.btnAction.contentDescription = "Report User"
            holder.btnAction.imageTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#FF9800"))
        } else {
            holder.btnAction.setImageResource(R.drawable.ic_delete)
            holder.btnAction.contentDescription = "Delete User"
            holder.btnAction.imageTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#F44336"))
        }

        if (user.role == "Owner") {
            holder.layoutExtra.visibility = View.VISIBLE
            holder.tvPhone.text = "Phone: ${user.phone ?: "N/A"}"
            holder.tvAddress.text = "Address: ${user.address ?: "N/A"}"
            holder.tvProfession.text = "Profession: ${user.profession ?: "N/A"}"
            holder.tvBio.text = "Bio: ${user.bio ?: "N/A"}"
        } else {
            holder.layoutExtra.visibility = View.GONE
        }

        if (!user.profileImageUrl.isNullOrEmpty()) {
            Glide.with(holder.itemView.context)
                .load(user.profileImageUrl)
                .placeholder(R.drawable.ic_person)
                .circleCrop()
                .into(holder.ivUser)
        } else {
            holder.ivUser.setImageResource(R.drawable.ic_person)
        }

        holder.btnAction.setOnClickListener { onActionClick(user) }
    }

    override fun getItemCount(): Int = users.size
}
