package com.example.myapplication.shared.reversefaq

/**
 * Reverse FAQの対象となる文書（契約書全般）を表す案件。
 */
data class DocumentCase(
    val id: Long = 0,
    val title: String,
    val documentUri: String? = null,
    val documentType: DocumentType = DocumentType.NONE,
    val createdAt: Long,
    val deadline: Long? = null,
    val status: CaseStatus = CaseStatus.DRAFT
)
