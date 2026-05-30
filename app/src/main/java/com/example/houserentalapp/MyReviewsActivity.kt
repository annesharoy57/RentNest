package com.example.houserentalapp

import android.app.AlertDialog
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*

class MyReviewsActivity : AppCompatActivity() {

    private lateinit var rvReviews: RecyclerView
    private lateinit var pbReviews: ProgressBar
    private lateinit var layoutNoReviews: View
    private val reviewList = mutableListOf<Review>()
    private lateinit var adapter: MyReviewAdapter

    private val auth = FirebaseAuth.getInstance()
    private val rootDatabase = FirebaseDatabase.getInstance().reference
    
    private var reviewsRef: DatabaseReference? = null
    private var reviewsListener: ValueEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_my_reviews)

        rvReviews = findViewById(R.id.rvMyReviews)
        pbReviews = findViewById(R.id.pbReviews)
        layoutNoReviews = findViewById(R.id.tvNoReviews)

        findViewById<View>(R.id.btnBackReviews).setOnClickListener { finish() }

        rvReviews.layoutManager = LinearLayoutManager(this)
        adapter = MyReviewAdapter(
            reviewList,
            onEditClick = { review -> showEditReviewDialog(review) },
            onDeleteClick = { review -> showDeleteConfirmation(review) }
        )
        rvReviews.adapter = adapter

        loadMyReviews()
    }

    private fun loadMyReviews() {
        val userId = auth.currentUser?.uid ?: return
        pbReviews.visibility = View.VISIBLE

        reviewsRef = rootDatabase.child("UserReviews").child(userId)
        reviewsListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                reviewList.clear()
                for (data in snapshot.children) {
                    val review = data.getValue(Review::class.java)
                    if (review != null) {
                        review.reviewId = data.key
                        reviewList.add(review)
                    }
                }
                pbReviews.visibility = View.GONE
                layoutNoReviews.visibility = if (reviewList.isEmpty()) View.VISIBLE else View.GONE
                adapter.notifyDataSetChanged()
            }

            override fun onCancelled(error: DatabaseError) {
                pbReviews.visibility = View.GONE
            }
        }
        reviewsRef?.addValueEventListener(reviewsListener!!)
    }

    override fun onDestroy() {
        reviewsListener?.let { reviewsRef?.removeEventListener(it) }
        super.onDestroy()
    }

    private fun showEditReviewDialog(review: Review) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_review)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        val ratingBar = dialog.findViewById<RatingBar>(R.id.ratingBar)
        val etReview = dialog.findViewById<EditText>(R.id.etReview)
        val btnUpdate = dialog.findViewById<Button>(R.id.btnPostReview)
        val btnCancel = dialog.findViewById<Button>(R.id.btnCancelReview)

        btnUpdate.text = "Update"
        ratingBar.rating = review.rating
        etReview.setText(review.review)

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnUpdate.setOnClickListener {
            val newRating = ratingBar.rating
            val newReviewText = etReview.text.toString().trim()
            val userId = auth.currentUser?.uid ?: return@setOnClickListener
            val rId = review.reviewId ?: ""
            val pId = review.propertyId ?: ""

            if (pId.isEmpty() || rId.isEmpty()) {
                Toast.makeText(this, "Error: Missing review ID", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val updates = hashMapOf<String, Any?>()
            updates["/Reviews/$pId/$rId/rating"] = newRating
            updates["/Reviews/$pId/$rId/review"] = newReviewText
            updates["/UserReviews/$userId/$rId/rating"] = newRating
            updates["/UserReviews/$userId/$rId/review"] = newReviewText

            rootDatabase.updateChildren(updates).addOnSuccessListener {
                // FORCE SYNC: Find owner and update their notification
                syncWithPropertyOwner(pId, userId, rId, newReviewText, newRating, false)
                Toast.makeText(this, "Review updated everywhere!", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }.addOnFailureListener {
                Toast.makeText(this, "Update failed: ${it.message}", Toast.LENGTH_SHORT).show()
            }
        }
        dialog.show()
    }

    private fun showDeleteConfirmation(review: Review) {
        AlertDialog.Builder(this)
            .setTitle("Delete Review")
            .setMessage("Delete this review from the property and owner notifications?")
            .setPositiveButton("Delete") { _, _ -> deleteReview(review) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteReview(review: Review) {
        val userId = auth.currentUser?.uid ?: return
        val rId = review.reviewId ?: ""
        val pId = review.propertyId ?: ""

        if (pId.isEmpty() || rId.isEmpty()) return

        val updates = hashMapOf<String, Any?>()
        updates["/Reviews/$pId/$rId"] = null
        updates["/UserReviews/$userId/$rId"] = null

        rootDatabase.updateChildren(updates).addOnSuccessListener {
            // FORCE SYNC: Find owner and delete the notification
            syncWithPropertyOwner(pId, userId, rId, null, 0f, true)
            Toast.makeText(this, "Review deleted everywhere", Toast.LENGTH_SHORT).show()
        }
    }

    private fun syncWithPropertyOwner(pId: String, userId: String, rId: String, text: String?, rating: Float, isDelete: Boolean) {
        // Find owner from Property node to ensure we have the correct ID
        rootDatabase.child("Properties").child(pId).child("ownerId")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val ownerId = snapshot.value?.toString() ?: return
                    
                    val notifyRef = rootDatabase.child("Notifications").child(ownerId)
                    notifyRef.addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(notifySnapshot: DataSnapshot) {
                            for (data in notifySnapshot.children) {
                                val type = data.child("type").value?.toString()
                                val targetPid = data.child("propertyId").value?.toString()
                                val fromUid = data.child("fromUserId").value?.toString()
                                
                                if (type == "REVIEW" && targetPid == pId && fromUid == userId) {
                                    if (isDelete) {
                                        data.ref.removeValue()
                                    } else {
                                        val notifyUpdates = hashMapOf<String, Any?>()
                                        notifyUpdates["reviewText"] = text
                                        notifyUpdates["rating"] = rating
                                        data.ref.updateChildren(notifyUpdates)
                                    }
                                }
                            }
                        }
                        override fun onCancelled(error: DatabaseError) {}
                    })
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }
}

class MyReviewAdapter(
    private val reviewList: List<Review>,
    private val onEditClick: (Review) -> Unit,
    private val onDeleteClick: (Review) -> Unit
) : RecyclerView.Adapter<MyReviewAdapter.ReviewViewHolder>() {

    class ReviewViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivProperty: ImageView = view.findViewById(R.id.ivReviewProperty)
        val tvTitle: TextView = view.findViewById(R.id.tvReviewPropertyTitle)
        val tvLocation: TextView = view.findViewById(R.id.tvReviewLocation)
        val ratingBar: RatingBar = view.findViewById(R.id.reviewRatingBar)
        val tvText: TextView = view.findViewById(R.id.tvReviewText)
        val tvDate: TextView = view.findViewById(R.id.tvReviewDate)
        val btnEdit: View = view.findViewById(R.id.btnEditReview)
        val btnDelete: View = view.findViewById(R.id.btnDeleteReview)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReviewViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_review, parent, false)
        return ReviewViewHolder(view)
    }

    override fun onBindViewHolder(holder: ReviewViewHolder, position: Int) {
        val review = reviewList[position]
        holder.tvTitle.text = review.propertyTitle ?: "House Rental"
        holder.tvLocation.text = review.propertyLocation ?: "Location"
        holder.ratingBar.rating = review.rating
        holder.tvText.text = review.review
        
        val ts = review.timestamp
        if (ts is Long) {
            val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            holder.tvDate.text = sdf.format(Date(ts))
        }

        Glide.with(holder.itemView.context)
            .load(review.propertyImage)
            .placeholder(R.drawable.ic_home)
            .into(holder.ivProperty)

        holder.btnEdit.setOnClickListener { onEditClick(review) }
        holder.btnDelete.setOnClickListener { onDeleteClick(review) }
    }

    override fun getItemCount(): Int = reviewList.size
}
