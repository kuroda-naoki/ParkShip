package ecccomp.team_create4.parkship

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Point
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import com.google.android.gms.location.*
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*
import com.google.firebase.database.*
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import kotlin.properties.Delegates

private val ARR_MAX: Int = 30
private val MAX_MARKER: Int = 25

class map : Fragment(), OnMapReadyCallback, LocationListener {

    lateinit var mMap: GoogleMap

    private lateinit var database: DatabaseReference
    private lateinit var dbRef: DatabaseReference
    private lateinit var rpRef: DatabaseReference

    lateinit var fusedLocationProviderClient : FusedLocationProviderClient
    var locationCallback: LocationCallback? = null

    lateinit var nowLocation: LatLng
    var nowMarker: Marker? = null

    private var Park_Marker: ArrayList<Marker> = ArrayList()
    private var Park_LatLng: ArrayList<LatLng> = ArrayList()
    private var Park_ID: ArrayList<String> = ArrayList()
    private var Park_Address: ArrayList<String> = ArrayList()
    private var Park_Name: ArrayList<String> = ArrayList()
    private var Park_Count: ArrayList<String> = ArrayList()

    //MainActivityからユーザ情報を取得するデータ
    lateinit var Account_ID: String
    lateinit var Account_RP: String

    //公園詳細画面に送るデータ
    private var Park_Bundle: Bundle = Bundle()



    lateinit var sbounds: LatLngBounds
    lateinit var ebounds: LatLngBounds
    var dbconn_flg: Boolean = false


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requireActivity().setTitle("ParkShip")

        Account_ID = arguments?.getString("id").toString()
        Account_RP = arguments?.getString("rpcount").toString()
        Log.d("account", "account id : $Account_ID")
        Log.d("account", "account rp : $Account_RP")

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        database = Firebase.database.reference

        Log.d("firebase", "onCreate Through#####")


        /** DBに通報データを追加するコード
        dbRef = Firebase.database.getReference("ksj:Dataset")
        for (i in 0..5111){
            dbRef.child("elm:Report").child("$i").child("-gml:id").setValue("${i+1}")
        } **/

        /** DBに通報数カウントデータを追加するコード
        rpRef = Firebase.database.getReference("ksj:Dataset/elm:Report")
        for (i in 0..5111){
            rpRef = Firebase.database.getReference("ksj:Dataset/ksj:Park/${i}")
            rpRef.child("count").setValue("0")
        } **/

        /** アカウントを追加するコード
        val acRef: DatabaseReference = Firebase.database.getReference("ksj:Dataset")
        acRef.child("usr:Account").child("0").child("id").setValue("1")
        acRef.child("usr:Account").child("0").child("rpcount").setValue("0")
        acRef.child("usr:Account").child("0").child("name").setValue("井石太郎")
        acRef.child("usr:Account").child("0").child("pass").setValue("123qwecc")
        acRef.child("usr:Account").child("0").child("friend").child("0").setValue("1") **/



        val postListener = object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                // Get Post object and use the values to update the UI
                val post = dataSnapshot.value

                val dbdata = database.child("ksj:Dataset").child("gml:Point")
                val dbdatail = database.child("ksj:Dataset").child("ksj:Park")

                for (i in 0..ARR_MAX){
                    dbdata.child("$i").child("gml:pos").get()
                        .addOnSuccessListener {
                            val ltString = it.value as String
                            val Park_String = ltString.split(" ") as ArrayList<String>
                            val latitude = Park_String.get(0).toDouble()
                            val longitude = Park_String.get(1).toDouble()
                            Park_LatLng.add(LatLng(latitude, longitude))
                        }
                    dbdatail.child("$i").get()
                        .addOnSuccessListener {
                            val ParkData: HashMap<String, ArrayList<String>> = it.value as HashMap<String, ArrayList<String>>
                            Log.d("firemap", "ID : ${ParkData.get("-gml:id")}")
                            Park_ID.add(ParkData.get("-gml:id").toString())
                            Log.d("firemap", "住所 : ${ParkData.get("ksj:pop")} ${ParkData.get("ksj:cop")}")
                            Park_Address.add("${ParkData.get("ksj:pop").toString()} ${ParkData.get("ksj:cop").toString()}")
                            Log.d("firemap", "公園名 : ${ParkData.get("ksj:nop")}")
                            Park_Name.add(ParkData.get("ksj:nop").toString())
                            Park_Count.add(ParkData.get("count").toString())
                            if (i == ARR_MAX){
                                dbconn_flg = true
                                MarkerInput()
                            }
                        }
                }
            }

            override fun onCancelled(databaseError: DatabaseError) {
                // Getting Post failed, log a message
                Log.w(ContentValues.TAG, "loadPost:onCancelled", databaseError.toException())
            }
        }
        database.addValueEventListener(postListener)

        val mkRef: DatabaseReference = Firebase.database.getReference("ksj:Dataset/ksj:Park")

//        mkRef.addValueEventListener(object : ValueEventListener{
//            override fun onDataChange(snapshot: DataSnapshot) {
//
//            }
//
//            override fun onCancelled(databaseError: DatabaseError) {
//                // Getting Post failed, log a message
//                Log.w(ContentValues.TAG, "loadPost:onCancelled", databaseError.toException())
//            }
//        })

    }


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val fragmentView =  inflater.inflate(R.layout.fragment_map, container, false)
        return fragmentView
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        //mMap.projection.visibleRegion
        val mapFragment =
            childFragmentManager.findFragmentById(R.id.mapView) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }


    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.setInfoWindowAdapter(ParkInfoWindow(requireContext()))

        var nowMarker: ArrayList<Marker> = ArrayList()

        //パーミッションの確認
        checkPermission()

        mMap.setOnCameraIdleListener {
            nowMarker.clear()
            if (::sbounds.isInitialized) ebounds = sbounds
            sbounds = mMap.projection.visibleRegion.latLngBounds
            Log.d("googlemap", "$sbounds")
            if(::ebounds.isInitialized) NowMarkerInput(sbounds, ebounds) else NowMarkerInput(sbounds)
        }

        //マーカーの詳細をタップした時の処理
        mMap.setOnInfoWindowClickListener {
            var fragment = ParkDetailFragment()

            Log.d("firemap", "click_id : ${it.id.removePrefix("m")}")
            Log.d("firemap", "detail : ${it.tag}, ${it.snippet}, ${it.title}")

            val Park_Tag = it.tag.toString().split(" ") as ArrayList<String>

            //Park_Bundle.putString("id", Park_ID.get(id))
            Park_Bundle.putString("account", Account_ID)
            Park_Bundle.putString("rpcount", Account_RP)
            Park_Bundle.putString("id", Park_Tag[0])
            Park_Bundle.putString("address", it.snippet)
            Park_Bundle.putString("name", it.title)
            Park_Bundle.putString("count", Park_Tag[1])

            fragment.setArguments(Park_Bundle)


            val postListener = object : ValueEventListener {
                override fun onDataChange(dataSnapshot: DataSnapshot) {
                }

                override fun onCancelled(databaseError: DatabaseError) {
                    // Getting Post failed, log a message
                    Log.w(ContentValues.TAG, "loadPost:onCancelled", databaseError.toException())
                }
            }

            childFragmentManager.beginTransaction()
                .replace(R.id.container, fragment)
                .commit()
        }
    }

    var MarkerColor: Float = BitmapDescriptorFactory.HUE_GREEN
    val MarkerGreen: Float = BitmapDescriptorFactory.HUE_GREEN          //0
    val MarkerYellow: Float = BitmapDescriptorFactory.HUE_YELLOW        //1~3
    val MarkerOrange: Float = BitmapDescriptorFactory.HUE_ORANGE        //4~10
    val MarkerRed: Float = BitmapDescriptorFactory.HUE_RED              //11~

    fun MarkerInput(){
        for (i in 0..ARR_MAX){
            Park_Marker.add(mMap.addMarker(MarkerOptions().position(Park_LatLng[i]).title(Park_Name[i]).snippet("Parkdayo!!!")
                .icon(BitmapDescriptorFactory.defaultMarker(MarkerColor)))!!)
            //Park_Marker[i].setTag(Park_ID[i])
            Park_Marker[i].remove()
        }
        if (::sbounds.isInitialized) NowMarkerInput(sbounds)
    }

    fun MarkerOutput(i: Int){
        Log.d("markercolor", "count : ${Park_Count[i]}")
        when (Park_Count[i].toInt()){
            0        -> MarkerColor = MarkerGreen
            in 1..3  -> MarkerColor = MarkerYellow
            in 4..10 -> MarkerColor = MarkerOrange
            else     -> MarkerColor = MarkerRed
        }
        Park_Marker[i] = mMap.addMarker(MarkerOptions().position(Park_LatLng[i]).title(Park_Name[i]).snippet("${Park_Address[i]}\n通報件数 : ${Park_Count[i]}")
            .icon(BitmapDescriptorFactory.defaultMarker(MarkerColor)))!!
        Park_Marker[i].setTag(Park_ID[i] + " " + Park_Count[i])
    }

    fun NowMarkerInput(bounds: LatLngBounds){
        if (dbconn_flg){
            for (i in 0..ARR_MAX){
                if (bounds.contains(Park_Marker[i].position)) {
                    Log.d("googlemap", "now Marker : ${Park_LatLng[i]}")
                    MarkerOutput(i)
                }
            }
        }
    }

    fun NowMarkerInput(sbounds: LatLngBounds, ebounds: LatLngBounds){
        if (dbconn_flg){
            for (i in 0..ARR_MAX){
                val marker: Marker = Park_Marker[i]
                if (sbounds.contains(marker.position) && !(ebounds.contains(marker.position))) {
                    Log.d("googlemap", "add Marker : ${marker.position}")
                    MarkerOutput(i)
                }else if (!(sbounds.contains(marker.position)) && ebounds.contains(marker.position)){
                    Log.d("googlemap", "del Marker : ${marker.position}")
                    Park_Marker[i].remove()
                }
            }
        }
    }


    //パーミッションの状態を確認する
    private fun checkPermission(){
        if(ActivityCompat.checkSelfPermission(requireActivity(), Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED){
            Log.d("perm1", "状態OK")
            myLocationEnable()
        }else{
            Log.d("perm1", "状態NG")
            ActivityCompat.requestPermissions(requireActivity(), arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),1)
        }
    }


    //requestPeermissionsのコールバック
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if(grantResults[0] == PackageManager.PERMISSION_GRANTED){
            Log.d("perm1", "許可されました")
            myLocationEnable()
        }else{
            Log.d("perm1", "拒否されました")
        }
    }


    //自分の位置情報をオンにする
    private fun myLocationEnable(){
        if (ActivityCompat.checkSelfPermission(
                requireActivity(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                requireActivity(),
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            //許可されていない
            Log.d("perm1", "位置情報が許可されていません")
            return
        }else{
            Log.d("perm1", "位置情報ON")
            mMap.isMyLocationEnabled = true

            val tasklc = fusedLocationProviderClient?.lastLocation

            tasklc.addOnSuccessListener { location ->
                if (location != null){
                    nowLocation = LatLng(location.latitude, location.longitude)
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(nowLocation, 13f))
                }
            }

            locationCallback = object : LocationCallback() {
                override fun onLocationResult(p0: LocationResult) {
                    super.onLocationResult(p0)
                    nowLocation = LatLng(p0.lastLocation.latitude, p0.lastLocation.longitude)
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(nowLocation, 13f))
                    Log.d("perm1", "Lat : ${nowLocation.latitude}, Long : ${nowLocation.longitude}")
                }
            }
        }
    }


    override fun onLocationChanged(location: Location) {
        Log.d("perm1", "現在地が更新されました")

        nowMarker?.let {
            it.remove()
        }

        location?.let {
            nowLocation = LatLng(it.latitude, it.longitude)
            nowMarker = mMap.addMarker(MarkerOptions().position(nowLocation).title("現在地!!!"))
        }
    }

}
