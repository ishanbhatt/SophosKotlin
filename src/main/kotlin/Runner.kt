import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.lang.IllegalArgumentException
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

import net.lingala.zip4j.ZipFile
import java.util.concurrent.locks.ReentrantLock


val uriList: MutableList<String> = arrayListOf()
val lock = ReentrantLock()
const val baseUrl = "http://mbd.hu/uris/"
const val dateRegexString =  "[0-9]{4}_[0-9]{2}_[0-9]{2}_[0-9]{2}_[0-9]{2}_[0-9]{2}"

fun String.getDatePattern():String = Regex(dateRegexString).find(this)?.value ?: throw IllegalArgumentException("Wrong Url")
val String.fullUrl: String
    get() = "$baseUrl${this.getDatePattern()}/urilist.zip"

fun String.saveTo(path: String) = URL(this).openStream().use {input ->
    FileOutputStream(File(path)).use { output ->
        input.copyTo(output)
    }
}

fun String.getEpoch(): String {
    val timeStamp: SimpleDateFormat = SimpleDateFormat("yyyy_MM_dd_HH_mm_ss")
    timeStamp.timeZone = TimeZone.getTimeZone("GMT")
    val date = timeStamp.parse(this.getDatePattern())
    return (date.time/1000).toString()
}

fun String.getHtmlText(): String = try { URL(this).readText() } catch (e: FileNotFoundException ) {"INVALID"}

fun extractCollectUrls(zipFilePath: String) {
    val zipFile = ZipFile(zipFilePath)
    zipFile.setPassword(zipFilePath.toCharArray())
    zipFile.extractAll(".")
    File("urilist.txt").renameTo(File("${zipFilePath}.txt"))
    val lines = File("${zipFilePath}.txt").bufferedReader().readLines()
    uriList.addAll(lines)
    File(zipFilePath).delete()
    File("${zipFilePath}.txt").delete()
}

fun main(args: Array<String>) {

    var text =  baseUrl.getHtmlText()
    var oldEpoch = text.getEpoch()
    var newEpoch: String
    val fileList: MutableList<String> = arrayListOf()

//    1) collect all file names in a single file and then iterate over them in Threaded Way (DOne in Scala code)
//    2) Collect 1 file do processing on it in a single thread. Spawn a new one every time change in file
    for (i in 1..args[0].toInt()) {
        try {
            text = baseUrl.getHtmlText()
            newEpoch = text.getEpoch()
        } catch (e: IllegalArgumentException) { continue }


        if(oldEpoch != newEpoch) {
            var fullUrlPath = text.fullUrl
            fullUrlPath.saveTo(oldEpoch)
            fileList.add(oldEpoch)
            oldEpoch = newEpoch
        }
        else {
            println("Same Epoch for iteration $i")
            Thread.sleep(2000)
        }
    }
    fileList.forEach {
        extractCollectUrls(it)
    }

    val mostOccurringUri = uriList.groupBy { it }.mapValues { it.value.size }.maxBy { it.value } ?.key
    println("Most occurring URI is $mostOccurringUri")
    println("Message it contains is ${URL(mostOccurringUri).readText()}")
}