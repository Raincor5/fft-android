package com.example.labels

data class LabelResponse(
    val label_id: String,
    val parsed_data: ParsedData,
    val raw_text: String
)

data class ParsedData(
    val batch_no: String,               // Batch number
    val dates: List<String>,            // List of dates (supports all types)
    val employee_name: String,          // Name of the employee
    val expiry_day: String,             // Expiry day (e.g., SUNDAY)
    val label_type: String,             // Label type: Normal or Defrosted
    val product_name: String,           // Product name
    val rte_status: String?,            // Ready-to-Eat status (nullable)

    // Additional fields for defrost labels
    val defrost_date: String?,          // Defrost date (optional)
    val ready_to_prep_date: String?,    // Ready to prep date (optional)
    val use_by_date: String?            // Use by date (optional, already exists in dates)
)
