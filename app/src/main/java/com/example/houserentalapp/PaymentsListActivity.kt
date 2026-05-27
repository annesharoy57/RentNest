package com.example.houserentalapp

import android.os.Bundle
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.util.*

class PaymentsListActivity : AppCompatActivity() {

    private lateinit var rvPayments: RecyclerView
    private lateinit var pbPayments: ProgressBar
    private lateinit var tvNoPayments: TextView
    private lateinit var tvTitle: TextView
    
    private val paymentList = mutableListOf<Payment>()
    private lateinit var adapter: PaymentsAdapter
    
    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance().getReference("Payments")
    
    private var paymentsQuery: Query? = null
    private var paymentsListener: ValueEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_payments_list)

        val isOwner = intent.getBooleanExtra("IS_OWNER", false)

        rvPayments = findViewById(R.id.rvPayments)
        pbPayments = findViewById(R.id.pbPayments)
        tvNoPayments = findViewById(R.id.tvNoPayments)
        tvTitle = findViewById(R.id.tvPaymentsTitle)

        tvTitle.text = if (isOwner) "Rent Tracker" else "Payment History"

        findViewById<View>(R.id.btnBackPaymentsList).setOnClickListener { finish() }

        rvPayments.layoutManager = LinearLayoutManager(this)
        adapter = PaymentsAdapter(paymentList, isOwner) { payment ->
            showDeleteDialog(payment)
        }
        rvPayments.adapter = adapter

        loadPayments(isOwner)
    }

    private fun loadPayments(isOwner: Boolean) {
        val userId = auth.currentUser?.uid ?: return
        pbPayments.visibility = View.VISIBLE

        paymentsQuery = if (isOwner) {
            database.orderByChild("ownerId").equalTo(userId)
        } else {
            database.orderByChild("userId").equalTo(userId)
        }

        paymentsListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                paymentList.clear()
                for (data in snapshot.children) {
                    val payment = data.getValue(Payment::class.java)
                    if (payment != null) {
                        payment.paymentId = data.key
                        paymentList.add(payment)
                    }
                }
                paymentList.sortByDescending { it.timestamp }
                
                pbPayments.visibility = View.GONE
                tvNoPayments.visibility = if (paymentList.isEmpty()) View.VISIBLE else View.GONE
                adapter.notifyDataSetChanged()
            }

            override fun onCancelled(error: DatabaseError) {
                pbPayments.visibility = View.GONE
            }
        }
        paymentsQuery?.addValueEventListener(paymentsListener!!)
    }

    private fun showDeleteDialog(payment: Payment) {
        AlertDialog.Builder(this)
            .setTitle("Remove Fraud Payment?")
            .setMessage("If this user didn't actually send money, deleting this will remove the history and subtract ৳${payment.amount} from your Revenue.")
            .setPositiveButton("Delete & Minus Revenue") { _, _ ->
                processPaymentDeletion(payment)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun processPaymentDeletion(payment: Payment) {
        val ownerId = auth.currentUser?.uid ?: return
        val amountStr = payment.amount?.replace("[^0-9]".toRegex(), "") ?: "0"
        val amount = amountStr.toLongOrNull() ?: 0L
        val rootRef = FirebaseDatabase.getInstance().reference
        val paymentId = payment.paymentId ?: return

        // 1. Deduct from Revenue
        val revenueRef = rootRef.child("Revenue").child(ownerId)
        revenueRef.runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                val currentRevenue = currentData.getValue(Long::class.java) ?: 0L
                currentData.value = if (currentRevenue >= amount) currentRevenue - amount else 0L
                return Transaction.success(currentData)
            }

            override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                if (committed) {
                    // 2. Remove from Payments History
                    database.child(paymentId).removeValue().addOnSuccessListener {
                        Toast.makeText(this@PaymentsListActivity, "Record deleted and revenue updated", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    val msg = error?.message ?: "Transaction failed"
                    Toast.makeText(this@PaymentsListActivity, "Failed to update revenue: $msg", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    override fun onDestroy() {
        paymentsListener?.let { paymentsQuery?.removeEventListener(it) }
        super.onDestroy()
    }
}

class PaymentsAdapter(
    private val payments: List<Payment>, 
    private val isOwnerView: Boolean,
    private val onDeleteClick: (Payment) -> Unit
) : RecyclerView.Adapter<PaymentsAdapter.PaymentViewHolder>() {

    class PaymentViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivProperty: ImageView = view.findViewById(R.id.ivPaymentPropertyImage)
        val tvTitle: TextView = view.findViewById(R.id.tvPaymentPropertyTitle)
        val tvLocation: TextView = view.findViewById(R.id.tvPaymentPropertyLocation)
        val tvDate: TextView = view.findViewById(R.id.tvPaymentDate)
        val tvAmount: TextView = view.findViewById(R.id.tvPaymentAmount)
        val tvBkash: TextView = view.findViewById(R.id.tvPaymentBkashNumber)
        val tvTxnId: TextView = view.findViewById(R.id.tvPaymentTxnId)
        val layoutPayer: View = view.findViewById(R.id.layoutPayerInfo)
        val tvPayerName: TextView = view.findViewById(R.id.tvPayerName)
        val ivPayerPic: ImageView = view.findViewById(R.id.ivPayerProfilePic)
        val btnDelete: ImageButton = view.findViewById(R.id.btnDeletePayment)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PaymentViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_payment, parent, false)
        return PaymentViewHolder(view)
    }

    override fun onBindViewHolder(holder: PaymentViewHolder, position: Int) {
        val payment = payments[position]
        val context = holder.itemView.context
        
        holder.tvTitle.text = payment.propertyTitle ?: "House"
        holder.tvLocation.text = payment.propertyLocation ?: "No Location Info"
        holder.tvAmount.text = "৳ ${payment.amount}"
        holder.tvBkash.text = payment.bKashNumber ?: "N/A"
        holder.tvTxnId.text = payment.transactionId ?: "N/A"
        
        val calendar = Calendar.getInstance(Locale.getDefault())
        calendar.timeInMillis = payment.timestamp
        holder.tvDate.text = DateFormat.format("dd MMM yyyy, hh:mm a", calendar).toString()

        if (isOwnerView) {
            holder.layoutPayer.visibility = View.VISIBLE
            holder.tvPayerName.text = payment.userName ?: "Unknown User"
            holder.btnDelete.visibility = View.VISIBLE
            holder.btnDelete.setOnClickListener { onDeleteClick(payment) }
            
            if (!payment.userProfilePic.isNullOrEmpty()) {
                Glide.with(context).load(payment.userProfilePic).circleCrop().into(holder.ivPayerPic)
            } else {
                holder.ivPayerPic.setImageResource(R.drawable.ic_person)
            }
        } else {
            holder.layoutPayer.visibility = View.GONE
            holder.btnDelete.visibility = View.GONE
        }

        if (!payment.propertyImage.isNullOrEmpty()) {
            Glide.with(context).load(payment.propertyImage)
                .placeholder(R.drawable.ic_home).into(holder.ivProperty)
        } else {
            holder.ivProperty.setImageResource(R.drawable.ic_home)
        }
    }

    override fun getItemCount(): Int = payments.size
}
