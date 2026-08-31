package com.example.myapplication.shared.reversefaq

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReverseFaqDomainTest {

    @Test
    fun `DocumentCaseはプロパティを保持する`() {
        val case = DocumentCase(
            id = 1,
            title = "○○マンション 賃貸契約",
            documentUri = "content://dummy",
            documentType = DocumentType.PDF,
            createdAt = 1_700_000_000_000L,
            deadline = 1_700_100_000_000L,
            status = CaseStatus.IN_PROGRESS
        )

        assertEquals(1, case.id)
        assertEquals("○○マンション 賃貸契約", case.title)
        assertEquals("content://dummy", case.documentUri)
        assertEquals(DocumentType.PDF, case.documentType)
        assertEquals(1_700_000_000_000L, case.createdAt)
        assertEquals(1_700_100_000_000L, case.deadline)
        assertEquals(CaseStatus.IN_PROGRESS, case.status)
    }

    @Test
    fun `DocumentCaseのdocumentUriとdeadlineはnull許容`() {
        val case = DocumentCase(
            id = 0,
            title = "新規案件",
            documentUri = null,
            documentType = DocumentType.NONE,
            createdAt = 1_700_000_000_000L,
            deadline = null,
            status = CaseStatus.DRAFT
        )

        assertNull(case.documentUri)
        assertNull(case.deadline)
        assertEquals(DocumentType.NONE, case.documentType)
        assertEquals(CaseStatus.DRAFT, case.status)
    }

    @Test
    fun `Questionはプロパティを保持する`() {
        val question = Question(
            id = 1,
            caseId = 2,
            title = "退去時のクリーニング費用は必ず発生しますか？",
            reason = "退去時の費用負担条件が不明確です。",
            riskLevel = RiskLevel.HIGH,
            sourceText = "退去時には所定のクリーニング費用を...",
            sourcePage = 8,
            status = QuestionStatus.UNCONFIRMED
        )

        assertEquals(1, question.id)
        assertEquals(2, question.caseId)
        assertEquals("退去時のクリーニング費用は必ず発生しますか？", question.title)
        assertEquals("退去時の費用負担条件が不明確です。", question.reason)
        assertEquals(RiskLevel.HIGH, question.riskLevel)
        assertEquals("退去時には所定のクリーニング費用を...", question.sourceText)
        assertEquals(8, question.sourcePage)
        assertEquals(QuestionStatus.UNCONFIRMED, question.status)
    }

    @Test
    fun `QuestionのsourceTextとsourcePageはnull許容`() {
        val question = Question(
            id = 1,
            caseId = 2,
            title = "質問",
            reason = "理由",
            riskLevel = RiskLevel.LOW,
            sourceText = null,
            sourcePage = null,
            status = QuestionStatus.UNCONFIRMED
        )

        assertNull(question.sourceText)
        assertNull(question.sourcePage)
    }

    @Test
    fun `UserContextはcaseIdとattributesJsonを保持する`() {
        val context = UserContext(
            caseId = 1,
            attributesJson = """{"student":true,"firstTimeRenting":true}"""
        )

        assertEquals(1, context.caseId)
        assertEquals("""{"student":true,"firstTimeRenting":true}""", context.attributesJson)
    }

    @Test
    fun `QuestionAnswerはプロパティを保持する`() {
        val answer = QuestionAnswer(
            id = 1,
            questionId = 2,
            answerText = "一律33,000円かかるとの説明を受けた。",
            answeredBy = "○○不動産 担当者",
            answeredAt = 1_700_000_000_000L
        )

        assertEquals(1, answer.id)
        assertEquals(2, answer.questionId)
        assertEquals("一律33,000円かかるとの説明を受けた。", answer.answerText)
        assertEquals("○○不動産 担当者", answer.answeredBy)
        assertEquals(1_700_000_000_000L, answer.answeredAt)
    }

    @Test
    fun `QuestionAnswerのansweredByはnull許容`() {
        val answer = QuestionAnswer(
            id = 0,
            questionId = 1,
            answerText = "回答",
            answeredBy = null,
            answeredAt = 1_700_000_000_000L
        )

        assertNull(answer.answeredBy)
    }

    @Test
    fun `RiskLevelはHIGH MEDIUM LOWを持つ`() {
        assertEquals(RiskLevel.HIGH, RiskLevel.valueOf("HIGH"))
        assertEquals(RiskLevel.MEDIUM, RiskLevel.valueOf("MEDIUM"))
        assertEquals(RiskLevel.LOW, RiskLevel.valueOf("LOW"))
    }

    @Test
    fun `QuestionStatusはUNCONFIRMED CONFIRMEDを持つ`() {
        assertEquals(QuestionStatus.UNCONFIRMED, QuestionStatus.valueOf("UNCONFIRMED"))
        assertEquals(QuestionStatus.CONFIRMED, QuestionStatus.valueOf("CONFIRMED"))
    }
}
