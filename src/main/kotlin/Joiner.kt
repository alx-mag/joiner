import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVPrinter
import org.apache.commons.csv.CSVRecord
import java.io.File
import java.io.FileWriter
import java.nio.charset.Charset
import java.text.NumberFormat
import java.util.*


val decimalFormat = NumberFormat.getInstance(Locale.FRANCE)
const val DEFECT_NAME = "Defect_name"
const val DEFECT_DEPTH = "Defect_depth"

fun main() {
    join("D:\\dev\\kaz\\defects.csv", "D:\\dev\\kaz\\magnetogram.csv", "D:\\dev\\kaz\\result.csv")
}

fun CSVRecord.toDefectsRecord(): DefectRecord =
    DefectRecord(this[1].convertToDouble(), this[4], this[5].convertToDouble())

fun CSVRecord.getDistanceInMeters(): Double = this[3].convertToDouble() / 100_000
fun String.convertToDouble(): Double = decimalFormat.parse(this).toDouble()
fun String.toFile(): File = File(this)
fun List<String>.toRawCsvRecord(separator: String = ","): String = this.joinToString(separator)

fun join(defectFile: String, valuesFile: String, outputFile: String) =
    join(defectFile.toFile(), valuesFile.toFile(), outputFile.toFile())

val defectFormat = CSVFormat.DEFAULT.withDelimiter(';').withFirstRecordAsHeader()
val valuesFormat = CSVFormat.DEFAULT.withDelimiter(',').withFirstRecordAsHeader()

// Погрешность, игнорируемые хедеры
val settings: Settings = Settings(0.05, setOf("Tag", "Size", "Index", "CRC", "Time"))

fun join(defectFile: File, valuesFile: File, outputFile: File) {
    val defectsRecords = defectFormat.parse(defectFile.reader(Charset.defaultCharset())).records
        .map { it.toDefectsRecord() }
        .sortedBy { defectsRecord -> defectsRecord.distance }

    val defectsIterator = defectsRecords.iterator()

    val valuesParser = CSVParser.parse(valuesFile.inputStream(), Charset.defaultCharset(), valuesFormat)

    val valuesIterator = valuesParser.iterator()

    val resultHeader: List<String> = (valuesParser.headerNames + listOf(DEFECT_NAME, DEFECT_DEPTH)).filter { !settings.filterColumns.contains(it) }
    val writer = createWriter(outputFile, resultHeader)
    val joinedRecords = mutableListOf<MutableMap<String, String>>()

    var rowsCounter: Int = 1
    writer.printRecord(resultHeader)
    valuesIterator.forEach { valuesRecord ->
        rowsCounter++
        if (rowsCounter % 1000 == 0) println("$rowsCounter records processed...")

        val valuesRecordMap = valuesRecord.toMap()
        val defectRecord = getMatchingDefectRecord(valuesRecord, defectsRecords)
        valuesRecordMap[DEFECT_NAME] = defectRecord?.defect?.let { DefectConverter.convert(it) }?.toString() ?: "0"
        valuesRecordMap[DEFECT_DEPTH] = defectRecord?.depth?.toString() ?: "0"
        writer.printRecord(resultHeader.map { valuesRecordMap[it] }.toList())
    }

    while (valuesIterator.hasNext()) {
        val nextDefect = defectsIterator.next()
        while (valuesIterator.hasNext()) {
            val valuesRecord = valuesIterator.next()
            if (valuesRecord.getDistanceInMeters() >= nextDefect.distance) {
                valuesRecord.toMap().apply {
                    put(DEFECT_NAME, nextDefect.defect)
                    put(DEFECT_DEPTH, nextDefect.depth.toString())
                    joinedRecords.add(this)
                }
                break
            }
        }
    }

    println("Result: file:///${outputFile.absolutePath.replace('\\', '/')}")
}

fun getMatchingDefectRecord(valuesRecord: CSVRecord, defectRecords: List<DefectRecord>): DefectRecord? =
    defectRecords.find { isMatchDist(it, valuesRecord) }

fun isMatchDist(defectRecord: DefectRecord, valuesRecord: CSVRecord): Boolean =
    (valuesRecord["Dist"].convertToDouble() / 100_000).let {
        it >= defectRecord.distance - settings.defectShift && it <= defectRecord.distance + settings.defectShift
    }

fun createWriter(file: File, header: List<String>): CSVPrinter {
    val writer: FileWriter = FileWriter(file)
    return CSVPrinter(writer, CSVFormat.DEFAULT.withHeader())
}


data class DefectRecord(val distance: Double, val defect: String, val depth: Double)

data class Settings(val defectShift: Double, val filterColumns: Set<String>)

object DefectConverter {
    fun convert(value: String) = when (value.toLowerCase()) {
        "язва" -> 1
        "питтинг" -> 2
        "поперечная канавка" -> 3
        else -> "unknown"
    }
}