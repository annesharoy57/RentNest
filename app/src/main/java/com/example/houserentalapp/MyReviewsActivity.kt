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
    private val database = FirebaseDatabase.getInstance().getReference("UserReviews")

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

        database.child(userId).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                reviewList.clear()
                for (data in snapshot.children) {
                    val review = data.getValue(Review::class.java)
                    if (review != null) {
                        reviewList.add(review)
                    }
                }
                pbReviews.visibility = View.GONE
                if (reviewList.isEmpty()) {
                    layoutNoReviews.visibility = View.VISIBLE
                } else {
                    layoutNoReviews.visibility = View.GONE
                }
                adapter.notifyDataSetChanged()
            }

            override fun onCancelled(error: DatabaseError) {
                pbReviews.visibility = View.GONE
                Toast.makeText(this@MyReviewsActivity, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun showEditReviewDialog(review: Review) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_review)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        val ratingBar = dialog.findViewById<RatingBar>(R.id.ratingBar)
        val etReview = dialog.findViewById<EditText>(R.id.etReview)
        val tvRatingLabel = dialog.findViewById<TextView>(R.id.tvRatingLabel)
        val btnUpdate = dialog.findViewById<Button>(R.id.btnPostReview)
        val btnCancel = dialog.findViewById<Button>(R.id.btnCancelReview)
        val tvHeader = dialog.findViewById<TextView>(R.id.tvReviewDialogTitle)

        tvHeader?.text = "Edit Your Review"
        btnUpdate.text = "Update"
        ratingBar.rating = review.rating
        etReview.setText(review.review)
        tvRatingLabel.text = "Rating (${review.rating.toInt()}/5)"

        ratingBar.setOnRatingBarChangeListener { _, rating, _ ->
            tvRatingLabel.text = "Rating (${rating.toInt()}/5)"
        }

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnUpdate.setOnClickListener {
            val newRating = ratingBar.rating
            val newReviewText = etReview.text.toString().trim()

            if (newRating == 0f) {
                Toast.makeText(this, "Please select stars", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val userId = auth.currentUser?.uid ?: return@setOnClickListener
            val rId = review.reviewId ?: return@setOnClickListener
            val pId = review.propertyId ?: return@setOnClickListener

            val updates = hashMapOf<String, Any?>()
            updates["/Reviews/$pId/$rId/rating"] = newRating
            updates["/Reviews/$pId/$rId/review"] = newReviewText
            updates["/UserReviews/$userId/$rId/rating"] = newRating
            updates["/UserReviews/$userId/$rId/review"] = newReviewText

            FirebaseDatabase.getInstance().reference.updateChildren(updates).addOnSuccessListener {
                Toast.makeText(this, "Review updated!", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }.addOnFailureListener {
                Toast.makeText(this, "Update failed: ${it.message}", Toast.LENGTH_LONG).show()
            }
        }
        dialog.show()
    }

    private fun showDeleteConfirmation(review: Review) {
        AlertDialog.Builder(this)
            .setTitle("Delete Review")
            .setMessage("Are you sure you want to delete this review?")
            .setPositiveButton("Delete") { _, _ ->
                deleteReview(review)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteReview(review: Review) {
        val userId = auth.currentUser?.uid ?: return
        val rId = review.reviewId ?: return
        val pId = review.propertyId ?: return

        val updates = hashMapOf<String, Any?>()
        updates["/Reviews/$pId/$rId"] = null
        updates["/UserReviews/$userId/$rId"] = null

        FirebaseDatabase.getInstance().reference.updateChildren(updates).addOnSuccessListener {
            Toast.makeText(this, "Review deleted", Toast.LENGTH_SHORT).show()
        }.addOnFailureListener {
            Toast.makeText(this, "Delete failed: ${it.message}", Toast.LENGTH_SHORT).show()
        }
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
        val context = holder.itemView.context

        holder.tvTitle.text = review.propertyTitle ?: "House Rental"
        holder.tvLocation.text = review.propertyLocation ?: "Location details"
        holder.ratingBar.rating = review.rating
        holder.tvText.text = review.review
        
        val ts = review.timestamp
        if (ts is Long) {
            val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            holder.tvDate.text = sdf.format(Date(ts))
        } else {
            holder.tvDate.text = "Just now"
        }

        if (!review.propertyImage.isNullOrEmpty()) {
            Glide.with(context)
                .load(review.propertyImage)
                .placeholder(R.drawable.ic_home)
                .into(holder.ivProperty)
        } else {
            holder.ivProperty.setImageResource(R.drawable.ic_home)
        }

        holder.btnEdit.setOnClickListener { onEditClick(review) }
        holder.btnDelete.setOnClickListener { onDeleteClick(review) }
    }

    override fun getItemCount(): Int = reviewList.size
}
