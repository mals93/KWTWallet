package wallet.kiwinam.charlie.kwtwallet;

import org.web3j.protocol.Web3j;
import org.web3j.protocol.Web3jFactory;
import org.web3j.protocol.http.HttpService;

import okhttp3.WebSocket;

public class Web3jService {
    private static final Web3j ourInstance = Web3jFactory.build(new HttpService("https://ropsten.infura.io/v3/63ac23cf4447466cb966ac98e4dfbec3"));
    private static final Web3j observable = Web3jFactory.build(new HttpService("wss://ropsten.infura.io/ws"));
    //Web3j.providers.WebsocketProvider

    public static Web3j getInstance() {
        return ourInstance;
    }
    public static Web3j getObservable() { return observable; }
    // kiwi = 0x995fBcf172baDE023548eE0B0B4B184986b8811d
    // charlie = 0x1731d3be0Aa8C140A6a7bc5821f1C84A4f0b7E5b
    // park = 0x127176981dC3EaF9897B52bEC7B8F8dc0d6A7FEB
    private Web3jService() {
    }


}
