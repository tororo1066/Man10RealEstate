package red.man10.realestate.region

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import red.man10.realestate.Constants.Companion.mysqlQueue
import red.man10.realestate.Constants.Companion.regionData
import red.man10.realestate.Constants.Companion.worldRegion
import red.man10.realestate.MySQLManager
import red.man10.realestate.Plugin
import java.util.*

class RegionDatabase(private val pl: Plugin) {


    /////////////////////////////////
    //リージョンデータを新規で登録
    /////////////////////////////////
    fun registerRegion(data:RegionData,id:Int){

        val sql = "INSERT INTO `region` " +
                "(`server`, `world`, `owner_uuid`, `owner_name`, `name`, `status`, " +
                "`x`, `y`, `z`, `pitch`, `yaw`, " +
                "`sx`, `sy`, `sz`, `ex`, `ey`, `ez`) " +
                "VALUES (" +
                "'${data.server}', " +
                "'${data.world}', " +
                "'${data.owner_uuid}', " +
                "'${Bukkit.getPlayer(data.owner_uuid)!!.name}' ," +
                "'${data.name}', " +
                "'${data.status}', " +
                "'${data.teleport[0]}', "+
                "'${data.teleport[1]}', "+
                "'${data.teleport[2]}', "+
                "'${data.teleport[3]}', "+
                "'${data.teleport[4]}', "+
                "'${data.startCoordinate.first}', " +
                "'${data.startCoordinate.second}', " +
                "'${data.startCoordinate.third}', " +
                "'${data.endCoordinate.first}', " +
                "'${data.endCoordinate.second}', " +
                "'${data.endCoordinate.third}');"

        mysqlQueue.add(sql)

        regionData[id] = data

        val list = worldRegion[data.world]?: mutableListOf()
        list.add(id)
        worldRegion[data.world] = list

    }


    /**
     * テレポート地点の変更
     * @param id リージョンid
     * @param tp List<x,y,z,pitch,yaw>
     *
     */
    fun setRegionTeleport(id:Int,tp:MutableList<Double>){

        val data = regionData[id]?:return
        data.teleport = tp
        regionData[id] = data

        val sql = "UPDATE `region` SET `x`=${tp[0]},`y`=${tp[1]},`z`=${tp[2]}," +
                "`pitch`=${tp[3]},`yaw`=${tp[4]} WHERE `id`='$id';"

        mysqlQueue.add(sql)

    }

    //リージョンの値段を変更
    fun setPrice(id:Int,price:Double){

        val data = regionData[id]?:return
        data.price = price
        regionData[id] = data

        val sql =  "UPDATE `region` SET `price`='$price' WHERE  `id`='$id';"

        mysqlQueue.add(sql)

    }

    //ステータスの変更
    fun setRegionStatus(id:Int,status: String){

        val data = regionData[id]?:return
        data.status = status
        regionData[id] = data

        val sql =  "UPDATE `region` SET `status`='$status' WHERE  `id`='$id';"

        mysqlQueue.add(sql)
    }

    //オーナーの変更
    fun setRegionOwner(id:Int,owner: Player){

        val data = regionData[id]?:return
        data.owner_uuid = owner.uniqueId
        regionData[id] = data

        val sql = "UPDATE `region` SET `owner_uuid`='${owner.uniqueId}', `owner_name='${owner.name}' WHERE `id`='$id';"

        mysqlQueue.add(sql)

    }

    //賃料の変更
    fun setRent(id:Int,rent:Double){
        val data = regionData[id]?:return
        data.rent = rent
        regionData[id] = data

        mysqlQueue.add("UPDATE `region` SET `rent`=$rent WHERE `id`='$id';")
    }

    //スパンの変更
    fun setSpan(id:Int,span:Int){
        val data = regionData[id]?:return
        data.span = span
        regionData[id] = data

        mysqlQueue.add("UPDATE `region` SET `span`=$span WHERE `id`='$id';")
    }

    //土地の購入
    fun buy(id: Int,user:Player){

        val data = regionData[id]?:when{
            else -> {
                pl.sendMessage(user,"§3§l購入失敗！ 存在しないidです！")
                return
            }
        }

        if (user.uniqueId == data.owner_uuid){
            pl.sendMessage(user,"§3§lあなたはこのリージョンのオーナーです！")
            return
        }

        if (data.status != "onSale"){
            pl.sendMessage(user,"§3§lこのリージョンは販売中ではありません")
            return
        }

        if (pl.vault.getBalance(user.uniqueId) < data.price){
            pl.sendMessage(user,"§3§l所持金が足りません！")
            return
        }

        //旧オーナーに所持金を追加
        pl.vault.withdraw(user.uniqueId,data.price)

        setRegionOwner(id,user)
        setRegionStatus(id,"Protected")



        pl.sendMessage(user,"§a§l購入完了、土地の保護がされました！")
    }

    //リージョンの削除
    fun deleteRegion(id:Int){
        regionData.remove(id)

        mysqlQueue.add("DELETE FROM `region` WHERE  `id`=$id;")
        mysqlQueue.add("DELETE FROM `region_user` WHERE `region_id`=$id;")
    }

    ////////////////////////////////////
    //鯖起動時にリージョンのデータを読み込む
    ////////////////////////////////////
    fun loadRegion(){

        regionData.clear()

        val sql = "SELECT * FROM `region`;"

        val mysql = MySQLManager(pl,"man10estate")

        val rs = mysql.query(sql)?:return

        while (rs.next()){

            val id = rs.getInt("id")

            val data = RegionData()

            data.name = rs.getString("name")
            data.world = rs.getString("world")
            data.server = rs.getString("server")
            data.owner_uuid = UUID.fromString(rs.getString("owner_uuid"))
            data.status = rs.getString("status")
            data.price = rs.getDouble("price")

            data.rent = rs.getDouble("rent")
            data.span = rs.getInt("span")

            data.teleport = mutableListOf(
                    rs.getDouble("x"),
                    rs.getDouble("y"),
                    rs.getDouble("z"),
                    rs.getDouble("pitch"),
                    rs.getDouble("yaw")
            )
            data.startCoordinate = Triple(
                    rs.getDouble("sx"),
                    rs.getDouble("sy"),
                    rs.getDouble("sz")
            )
            data.endCoordinate = Triple(
                    rs.getDouble("ex"),
                    rs.getDouble("ey"),
                    rs.getDouble("ez")
            )

            regionData[id] = data

            val list = worldRegion[data.world]?: mutableListOf()
            list.add(id)
            worldRegion[data.world] = list
        }
        rs.close()
        mysql.close()
    }


    ///////////////////////////////
    //リージョンのデータをまとめたclass
    ///////////////////////////////
    class RegionData{

        var name = "RegionName"
        var owner_uuid : UUID = UUID.randomUUID()
        var status = "OnSale"

        var world = "world"
        var server = "server"

        var startCoordinate: Triple<Double,Double,Double> = Triple(0.0,0.0,0.0)
        var endCoordinate: Triple<Double,Double,Double> = Triple(0.0,0.0,0.0)
        var teleport = mutableListOf<Double>()

        var price : Double = 0.0

        var rent : Double = 0.0
        var span = 0 //0:month 1:week 2:day

    }
}