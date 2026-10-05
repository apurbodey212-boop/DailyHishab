package com.apurbo.dailyhisab

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Context.store by preferencesDataStore("daily_hisab")
data class Tx(val id: Long, val type: String, val note: String, val amount: Double, val date: String)

class MainActivity : ComponentActivity() {
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  setContent { HisabScreen() }
 }
}

@Composable
fun HisabScreen() {
 val context = androidx.compose.ui.platform.LocalContext.current
 val scope = rememberCoroutineScope()
 val dbKey = remember { stringPreferencesKey("transactions") }
 var txs by remember { mutableStateOf(listOf<Tx>()) }
 var note by remember { mutableStateOf("") }
 var amountText by remember { mutableStateOf("") }
 var message by remember { mutableStateOf("") }
 val dateFmt = remember { SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()) }

 fun save(list: List<Tx>) {
  txs = list
  val arr = JSONArray()
  list.forEach { t -> arr.put(JSONObject().put("id",t.id).put("type",t.type).put("note",t.note).put("amount",t.amount).put("date",t.date)) }
  scope.launch { context.store.edit { it[dbKey] = arr.toString() } }
 }
 fun add(type: String) {
  val n = amountText.toDoubleOrNull()
  if (note.isBlank() || n == null || n <= 0) { message = "বিবরণ ও সঠিক টাকার পরিমাণ দিন"; return }
  save(txs + Tx(System.currentTimeMillis(),type,note.trim(),n,dateFmt.format(Date())))
  note = ""; amountText = ""; message = "হিসাব সংরক্ষণ হয়েছে"
 }
 LaunchedEffect(Unit) {
  val raw = context.store.data.first()[dbKey] ?: "[]"
  txs = try {
   val a = JSONArray(raw)
   (0 until a.length()).map { i -> a.getJSONObject(i).let { Tx(it.getLong("id"),it.getString("type"),it.getString("note"),it.getDouble("amount"),it.getString("date")) } }
  } catch (_: Exception) { emptyList() }
 }
 val deposit = txs.filter { it.type == "জমা" }.sumOf { it.amount }
 val expense = txs.filter { it.type == "খরচ" }.sumOf { it.amount }
 MaterialTheme {
  Scaffold(topBar = { TopAppBar(title = { Text("Daily Hisab") }) }) { pad ->
   Column(Modifier.fillMaxSize().padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Text("মোট জমা: ৳ ${"%.2f".format(deposit)}")
    Text("মোট খরচ: ৳ ${"%.2f".format(expense)}")
    Text("ব্যালেন্স: ৳ ${"%.2f".format(deposit-expense)}", style = MaterialTheme.typography.headlineSmall)
    HorizontalDivider()
    Text("নতুন লেনদেন", style = MaterialTheme.typography.titleMedium)
    OutlinedTextField(note,{note=it},label={Text("বিবরণ")},modifier=Modifier.fillMaxWidth(),singleLine=true)
    OutlinedTextField(amountText,{ amountText=it.filter { c -> c.isDigit() || c=='.' } },label={Text("টাকার পরিমাণ")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),modifier=Modifier.fillMaxWidth(),singleLine=true)
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
     Button(onClick={add("জমা")},modifier=Modifier.weight(1f)) { Text("জমা যোগ") }
     Button(onClick={add("খরচ")},modifier=Modifier.weight(1f),colors=ButtonDefaults.buttonColors(containerColor=MaterialTheme.colorScheme.error)) { Text("খরচ যোগ") }
    }
    if (message.isNotBlank()) Text(message)
    Text("লেনদেনের তালিকা",style=MaterialTheme.typography.titleMedium)
    LazyColumn(verticalArrangement=Arrangement.spacedBy(6.dp)) {
     items(txs.sortedByDescending { it.id }, key={it.id}) { t ->
      Card(Modifier.fillMaxWidth()) {
       Row(Modifier.fillMaxWidth().padding(10.dp),horizontalArrangement=Arrangement.SpaceBetween) {
        Column(Modifier.weight(1f)) { Text(t.note); Text("${t.type} • ${t.date}",style=MaterialTheme.typography.bodySmall) }
        Text("${if(t.type=="জমা") "+" else "−"} ৳ ${"%.2f".format(t.amount)}")
        TextButton(onClick={save(txs.filterNot { it.id==t.id })}) { Text("মুছুন") }
       }
      }
     }
    }
   }
  }
 }
}
