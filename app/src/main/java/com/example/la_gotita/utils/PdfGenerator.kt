package com.example.la_gotita.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.la_gotita.ui.inventory.MonthSummary
import com.itextpdf.kernel.colors.ColorConstants
import com.itextpdf.kernel.colors.DeviceRgb
import com.itextpdf.kernel.font.PdfFont
import com.itextpdf.kernel.font.PdfFontFactory
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.borders.Border
import com.itextpdf.layout.element.AreaBreak
import com.itextpdf.layout.element.Cell
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Table
import com.itextpdf.layout.properties.AreaBreakType
import com.itextpdf.layout.properties.HorizontalAlignment
import com.itextpdf.layout.properties.TextAlignment
import com.itextpdf.layout.properties.UnitValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

object PdfGenerator {

    private val months = listOf(
        "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
        "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"
    )

    suspend fun generateInventoryReport(
        context: Context,
        productName: String,
        summaries: List<MonthSummary>,
        productPrice: Double,
        year: Int
    ): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            // Crear el directorio de reportes si no existe
            val reportsDir = File(context.getExternalFilesDir(null), "reportes")
            if (!reportsDir.exists()) {
                reportsDir.mkdirs()
            }

            // Nombre del archivo con timestamp
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "reporte_inventario_${productName.replace(" ", "_")}_$timestamp.pdf"
            val file = File(reportsDir, fileName)

            val writer = PdfWriter(FileOutputStream(file))
            val pdfDoc = PdfDocument(writer)
            val document = Document(pdfDoc)

            // Configurar fuentes
            val titleFont = PdfFontFactory.createFont()
            val headerFont = PdfFontFactory.createFont()
            val normalFont = PdfFontFactory.createFont()

            // Formato de números
            val nf = NumberFormat.getNumberInstance(Locale.getDefault())
            val moneyFormat = NumberFormat.getCurrencyInstance(Locale("es", "GT")).apply {
                currency = Currency.getInstance("GTQ")
            }

            // Título del documento
            val titleParagraph = Paragraph("REPORTE DE INVENTARIO")
                .setFont(titleFont)
                .setFontSize(20f)
                .setBold()
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(10f)
            document.add(titleParagraph)

            // Información del producto
            val productInfo = Paragraph("Producto: $productName")
                .setFont(headerFont)
                .setFontSize(14f)
                .setBold()
                .setMarginBottom(5f)
            document.add(productInfo)

            // Fecha de generación
            val dateInfo = Paragraph("Fecha de generación: ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())}")
                .setFont(normalFont)
                .setFontSize(12f)
                .setMarginBottom(5f)
            document.add(dateInfo)

            // Año del reporte
            val yearInfo = Paragraph("Año: $year")
                .setFont(normalFont)
                .setFontSize(12f)
                .setMarginBottom(20f)
            document.add(yearInfo)

            // Filtrar solo los resúmenes válidos (12 meses)
            val validSummaries = summaries.filter { it.monthIndex0 in 0..11 }

            // Crear tabla de unidades
            createUnitsTable(document, validSummaries, nf, headerFont, normalFont)

            // Salto de página
            document.add(AreaBreak(AreaBreakType.NEXT_PAGE))

            // Crear tabla de dinero
            createMoneyTable(document, validSummaries, productPrice, moneyFormat, headerFont, normalFont)

            // Crear tabla de totales
            createTotalsTable(document, validSummaries, productPrice, nf, moneyFormat, headerFont, normalFont)

            document.close()

            // Crear URI para compartir
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            Result.success(uri)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun createUnitsTable(
        document: Document,
        summaries: List<MonthSummary>,
        nf: NumberFormat,
        headerFont: PdfFont,
        normalFont: PdfFont
    ) {
        // Título de la tabla
        val unitsTitle = Paragraph("RESUMEN EN UNIDADES")
            .setFont(headerFont)
            .setFontSize(14f)
            .setBold()
            .setMarginBottom(10f)
        document.add(unitsTitle)

        // Crear tabla de unidades
        val unitsTable = Table(UnitValue.createPercentArray(floatArrayOf(20f, 20f, 20f, 20f, 20f)))
            .setWidth(UnitValue.createPercentValue(100f))

        // Headers
        val headerColor = DeviceRgb(59, 130, 246) // Color azul
        val headers = listOf("MES", "TOTAL", "ENTRADAS", "SALIDAS", "DISPONIBLE")

        headers.forEach { header ->
            val cell = Cell().add(Paragraph(header).setFont(headerFont).setBold().setFontColor(ColorConstants.WHITE))
                .setBackgroundColor(headerColor)
                .setTextAlignment(TextAlignment.CENTER)
                .setBorder(Border.NO_BORDER)
                .setPadding(8f)
            unitsTable.addCell(cell)
        }

        // Datos
        summaries.sortedBy { it.monthIndex0 }.forEach { summary ->
            val rowColor = if (summaries.indexOf(summary) % 2 == 0)
                DeviceRgb(248, 249, 250) else DeviceRgb(255, 255, 255)

            // Mes
            unitsTable.addCell(Cell().add(Paragraph(months[summary.monthIndex0]).setFont(normalFont))
                .setBackgroundColor(rowColor)
                .setTextAlignment(TextAlignment.CENTER)
                .setBorder(Border.NO_BORDER)
                .setPadding(6f))

            // Total
            unitsTable.addCell(Cell().add(Paragraph(if (summary.total == 0) "--" else nf.format(summary.total)).setFont(normalFont))
                .setBackgroundColor(rowColor)
                .setTextAlignment(TextAlignment.CENTER)
                .setBorder(Border.NO_BORDER)
                .setPadding(6f))

            // Entradas
            unitsTable.addCell(Cell().add(Paragraph(if (summary.entries == 0) "--" else nf.format(summary.entries)).setFont(normalFont))
                .setBackgroundColor(rowColor)
                .setTextAlignment(TextAlignment.CENTER)
                .setBorder(Border.NO_BORDER)
                .setPadding(6f))

            // Salidas
            unitsTable.addCell(Cell().add(Paragraph(if (summary.exits == 0) "--" else nf.format(summary.exits)).setFont(normalFont))
                .setBackgroundColor(rowColor)
                .setTextAlignment(TextAlignment.CENTER)
                .setBorder(Border.NO_BORDER)
                .setPadding(6f))

            // Disponible
            unitsTable.addCell(Cell().add(Paragraph(if (summary.available == 0) "--" else nf.format(summary.available)).setFont(normalFont))
                .setBackgroundColor(rowColor)
                .setTextAlignment(TextAlignment.CENTER)
                .setBorder(Border.NO_BORDER)
                .setPadding(6f))
        }

        document.add(unitsTable)
    }

    private fun createMoneyTable(
        document: Document,
        summaries: List<MonthSummary>,
        productPrice: Double,
        moneyFormat: NumberFormat,
        headerFont: PdfFont,
        normalFont: PdfFont
    ) {
        // Título de la tabla
        val moneyTitle = Paragraph("RESUMEN EN DINERO")
            .setFont(headerFont)
            .setFontSize(14f)
            .setBold()
            .setMarginBottom(10f)
        document.add(moneyTitle)

        // Crear tabla de dinero
        val moneyTable = Table(UnitValue.createPercentArray(floatArrayOf(20f, 20f, 20f, 20f, 20f)))
            .setWidth(UnitValue.createPercentValue(100f))

        // Headers
        val headerColor = DeviceRgb(34, 197, 94) // Color verde
        val headers = listOf("MES", "TOTAL", "ENTRADAS", "SALIDAS", "DISPONIBLE")

        headers.forEach { header ->
            val cell = Cell().add(Paragraph(header).setFont(headerFont).setBold().setFontColor(ColorConstants.WHITE))
                .setBackgroundColor(headerColor)
                .setTextAlignment(TextAlignment.CENTER)
                .setBorder(Border.NO_BORDER)
                .setPadding(8f)
            moneyTable.addCell(cell)
        }

        // Datos
        summaries.sortedBy { it.monthIndex0 }.forEach { summary ->
            val rowColor = if (summaries.indexOf(summary) % 2 == 0)
                DeviceRgb(248, 249, 250) else DeviceRgb(255, 255, 255)

            // Mes
            moneyTable.addCell(Cell().add(Paragraph(months[summary.monthIndex0]).setFont(normalFont))
                .setBackgroundColor(rowColor)
                .setTextAlignment(TextAlignment.CENTER)
                .setBorder(Border.NO_BORDER)
                .setPadding(6f))

            // Total
            val totalMoney = if (summary.total == 0) "--" else moneyFormat.format(summary.total * productPrice)
            moneyTable.addCell(Cell().add(Paragraph(totalMoney).setFont(normalFont))
                .setBackgroundColor(rowColor)
                .setTextAlignment(TextAlignment.CENTER)
                .setBorder(Border.NO_BORDER)
                .setPadding(6f))

            // Entradas
            val entriesMoney = if (summary.entries == 0) "--" else moneyFormat.format(summary.entries * productPrice)
            moneyTable.addCell(Cell().add(Paragraph(entriesMoney).setFont(normalFont))
                .setBackgroundColor(rowColor)
                .setTextAlignment(TextAlignment.CENTER)
                .setBorder(Border.NO_BORDER)
                .setPadding(6f))

            // Salidas
            val exitsMoney = if (summary.exits == 0) "--" else moneyFormat.format(summary.exits * productPrice)
            moneyTable.addCell(Cell().add(Paragraph(exitsMoney).setFont(normalFont))
                .setBackgroundColor(rowColor)
                .setTextAlignment(TextAlignment.CENTER)
                .setBorder(Border.NO_BORDER)
                .setPadding(6f))

            // Disponible
            val availableMoney = if (summary.available == 0) "--" else moneyFormat.format(summary.available * productPrice)
            moneyTable.addCell(Cell().add(Paragraph(availableMoney).setFont(normalFont))
                .setBackgroundColor(rowColor)
                .setTextAlignment(TextAlignment.CENTER)
                .setBorder(Border.NO_BORDER)
                .setPadding(6f))
        }

        document.add(moneyTable)
    }

    private fun createTotalsTable(
        document: Document,
        summaries: List<MonthSummary>,
        productPrice: Double,
        nf: NumberFormat,
        moneyFormat: NumberFormat,
        headerFont: PdfFont,
        normalFont: PdfFont
    ) {
        // Espacio antes de totales
        document.add(Paragraph("\n"))

        // Título de totales
        val totalsTitle = Paragraph("TOTALES ANUALES")
            .setFont(headerFont)
            .setFontSize(14f)
            .setBold()
            .setMarginBottom(10f)
        document.add(totalsTitle)

        // Calcular totales
        val totalEntries = summaries.sumOf { it.entries }
        val totalExits = summaries.sumOf { it.exits }
        val totalBalance = totalEntries - totalExits
        val totalAvailable = summaries.sumOf { it.available }

        // Crear tabla de totales
        val totalsTable = Table(UnitValue.createPercentArray(floatArrayOf(25f, 25f, 25f, 25f)))
            .setWidth(UnitValue.createPercentValue(100f))

        // Headers
        val headerColor = DeviceRgb(139, 69, 19) // Color marrón
        val headers = listOf("CONCEPTO", "UNIDADES", "DINERO", "DESCRIPCIÓN")

        headers.forEach { header ->
            val cell = Cell().add(Paragraph(header).setFont(headerFont).setBold().setFontColor(ColorConstants.WHITE))
                .setBackgroundColor(headerColor)
                .setTextAlignment(TextAlignment.CENTER)
                .setBorder(Border.NO_BORDER)
                .setPadding(8f)
            totalsTable.addCell(cell)
        }

        // Datos de totales
        val totalsData = listOf(
            Triple("TOTAL ENTRADAS", totalEntries, "Suma de todas las entradas del año"),
            Triple("TOTAL SALIDAS", totalExits, "Suma de todas las salidas del año"),
            Triple("BALANCE NETO", totalBalance, "Diferencia entre entradas y salidas"),
            Triple("STOCK DISPONIBLE", totalAvailable, "Stock actual disponible")
        )

        totalsData.forEachIndexed { index, (concept, units, description) ->
            val rowColor = if (index % 2 == 0) DeviceRgb(248, 249, 250) else DeviceRgb(255, 255, 255)

            // Concepto
            totalsTable.addCell(Cell().add(Paragraph(concept).setFont(normalFont).setBold())
                .setBackgroundColor(rowColor)
                .setTextAlignment(TextAlignment.LEFT)
                .setBorder(Border.NO_BORDER)
                .setPadding(6f))

            // Unidades
            totalsTable.addCell(Cell().add(Paragraph(if (units == 0) "--" else nf.format(units)).setFont(normalFont))
                .setBackgroundColor(rowColor)
                .setTextAlignment(TextAlignment.CENTER)
                .setBorder(Border.NO_BORDER)
                .setPadding(6f))

            // Dinero
            val moneyValue = if (units == 0) "--" else moneyFormat.format(units * productPrice)
            totalsTable.addCell(Cell().add(Paragraph(moneyValue).setFont(normalFont))
                .setBackgroundColor(rowColor)
                .setTextAlignment(TextAlignment.CENTER)
                .setBorder(Border.NO_BORDER)
                .setPadding(6f))

            // Descripción
            totalsTable.addCell(Cell().add(Paragraph(description).setFont(normalFont))
                .setBackgroundColor(rowColor)
                .setTextAlignment(TextAlignment.LEFT)
                .setBorder(Border.NO_BORDER)
                .setPadding(6f))
        }

        document.add(totalsTable)
    }

    fun shareReport(context: Context, uri: Uri) {
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(shareIntent, "Compartir reporte de inventario")
        context.startActivity(chooser)
    }
}
