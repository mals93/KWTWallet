package wallet.kiwinam.charlie.kwtwallet.wallet;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import com.tuyenmonkey.mkloader.MKLoader;

import org.jetbrains.annotations.NotNull;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.EthLog;
import org.web3j.protocol.core.methods.response.Transaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import java.io.IOException;
import java.math.BigInteger;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

import io.reactivex.Observable;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;
import wallet.kiwinam.charlie.kwtwallet.KeyStoreUtils;
import wallet.kiwinam.charlie.kwtwallet.R;
import wallet.kiwinam.charlie.kwtwallet.Web3jService;
import wallet.kiwinam.charlie.kwtwallet.contract.KiwiTestToken;
import wallet.kiwinam.charlie.kwtwallet.db.KeyDBHelper;
import wallet.kiwinam.charlie.kwtwallet.kakaopay.ChargeActivity;
import wallet.kiwinam.charlie.kwtwallet.wallet.transaction.TransactionAdapter;

public class WalletActivity extends AppCompatActivity implements View.OnClickListener {
    // View 객체들
    private TextView walletNameTv;              // 지갑 이름 텍스트 뷰
    private TextView walletAddressTv;           // 지갑 주소 텍스트뷰
    private TextView walletRefillTv;            // 충전하기 텍스트 뷰
    private TextView walletAccountTv;           // 현재 잔액 텍스트뷰
    private TextView walletNoTransactionTv;     // 트랜잭션 표시 텍스트뷰
    private TextView walletAddTokenTv;          // 토큰 요청 개수 표시 텍스트뷰

    private EditText walletNameEt;              // 지갑 이름 바꾸기 EditText

    private ImageView walletMenuIv;             // 메뉴 이미지 뷰
    private ImageView walletNameEditIv;         // 이름 수정 이미지 뷰
    private ImageView walletRefreshIv;          // 새로고침 이미지 뷰
    private ImageView walletNameConfirmIv;      // 이름 변경 확인 이미지 뷰

    private Button walletSendBtn;               // 보내기 버튼
    private Button walletReceiveBtn;            // 받기 버튼

    private LinearLayout walletAddrLo;          // 지갑 주소 레이아웃

    private SwipeRefreshLayout walletSwipeLo;   // 당겨서 새로고침 레이아웃
    private RecyclerView walletTransactionRv;   // 트랜잭션 RecyclerView

    private TransactionAdapter adapter;         // 트랜잭션 어댑터
    private ArrayList<wallet.kiwinam.charlie.kwtwallet.wallet.transaction.Transaction> transactionList;

    private MKLoader walletLoader;              // 보내기 프로그레스 로더
    private MKLoader walletValueLoader;         // 토큰 구매 로더
    private MKLoader walletRefreshLoader;       // 잔액 로더

    private String name, address;
    private Web3j web3j;
    private KiwiTestToken kiwiToken;

    private Boolean isInitWallet = false;
    private Boolean isFirstLoadValue = true;

    private DecimalFormat decimalFormat = new DecimalFormat("#,##0");

    private Animation rotateAnim;    // 리프레시 회전 애니메이션

    private KeyDBHelper keyDBHelper;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wallet);
        if(getIntent().getBooleanExtra("isAutoRun",false)){  // 이미 지갑을 연적이 있다면
            name = getSharedPreferences("CurrentWallet", MODE_PRIVATE).getString("name","none");
            address = getSharedPreferences("CurrentWallet", MODE_PRIVATE).getString("address","none");
        }else{  // 새로 지갑을 생성한 경우
            name = getIntent().getStringExtra("name");
            address = getIntent().getStringExtra("address");
        }

        bindViews();    // View 와 객체를 연결한다.
        walletRefreshLoader.setVisibility(View.VISIBLE);    // 처음 한 번만 보여지는 로더
        initWallet();   // 지갑을 초기화한다.
        rotateAnim = AnimationUtils.loadAnimation(getApplicationContext(),R.anim.anim_rotate);
        keyDBHelper = new KeyDBHelper(getApplicationContext(),"keyList",null,1);
        keyDBHelper.keyDB();
    }

    /*
     * View 와 객체를 연결한다.
     */
    private void bindViews(){
        walletNameTv = findViewById(R.id.walletNameTv);
        walletAddressTv = findViewById(R.id.walletAddressTv);
        walletRefillTv = findViewById(R.id.walletRefillTv);
        walletAccountTv = findViewById(R.id.walletAccountTv);
        walletNoTransactionTv = findViewById(R.id.walletNoTransactionTv);
        walletMenuIv = findViewById(R.id.walletMenuIv);
        walletNameEditIv = findViewById(R.id.walletNameEditIv);
        walletSendBtn = findViewById(R.id.walletSendBtn);
        walletReceiveBtn = findViewById(R.id.walletReceiveBtn);
        walletAddrLo = findViewById(R.id.walletAddrLo);
        walletSwipeLo = findViewById(R.id.walletSwipeLo);
        walletTransactionRv = findViewById(R.id.walletTransactionRv);
        walletRefreshIv = findViewById(R.id.walletRefreshIv);
        walletNameEt = findViewById(R.id.walletNameEt);
        walletNameConfirmIv = findViewById(R.id.walletNameConfirmIv);
        walletLoader = findViewById(R.id.walletLoader);
        walletRefreshLoader = findViewById(R.id.walletRefreshLoader);
        walletValueLoader = findViewById(R.id.walletValueLoader);
        walletAddTokenTv = findViewById(R.id.walletAddTokenTv);

        walletNameTv.setText(name);
        walletNameEt.setText(name);
        walletAddressTv.setText(address);
        setRecyclerView();
        setClickListeners();
    }

    /*
     * 트랜잭션 RecyclerView 를 설정한다.
     */
    private void setRecyclerView(){
        transactionList = new ArrayList<>();
        adapter = new TransactionAdapter(transactionList,getApplicationContext());

        walletTransactionRv.setAdapter(adapter);
        walletTransactionRv.setLayoutManager(new LinearLayoutManager(getApplicationContext()));

        walletSwipeLo.setOnRefreshListener(this::getTransactionLogs);

        //getTransactionLogs();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.walletMenuIv: // 메뉴 버튼
                break;
            case R.id.walletRefillTv:   // 충전하기
                Intent intent = new Intent(getApplicationContext(), ChargeActivity.class);
                intent.putExtra("token",walletAccountTv.getText().toString());  // 현재 잔액을 가져온다.
                intent.putExtra("address",address);
                startActivityForResult(intent,3001);
                //finish();
                break;
            case R.id.walletNameEditIv: // 지갑 이름 변경
                walletNameEt.setVisibility(View.VISIBLE);
                walletNameTv.setVisibility(View.GONE);

                walletNameEditIv.setVisibility(View.GONE);
                walletNameConfirmIv.setVisibility(View.VISIBLE);
                break;
            case R.id.walletNameConfirmIv:  // 지갑 이름 변경 확인
                String newWalletName = walletNameEt.getText().toString();
                SharedPreferences sharedPreferences = getSharedPreferences("CurrentWallet",MODE_PRIVATE);
                SharedPreferences.Editor editor = sharedPreferences.edit();

                editor.putString("name",newWalletName);
                editor.apply();

                keyDBHelper.updateWalletName(newWalletName, address);   // DB 업데이트

                walletNameTv.setText(newWalletName);    // 새로운 이름 설정
                walletNameEt.setVisibility(View.GONE);  // Et 숨김
                walletNameTv.setVisibility(View.VISIBLE);   // 텍스트뷰 보이기

                walletNameConfirmIv.setVisibility(View.GONE);   // 변경 확인 버튼 숨기기
                walletNameEditIv.setVisibility(View.VISIBLE );  // 변경 버튼 보이기
                break;
            case R.id.walletSendBtn:    // 보내기 버튼
                runSendBs("");
                break;
            case R.id.walletReceiveBtn: // 받기 버튼
                ReceiveBottomSheet receiveBottomSheet = new ReceiveBottomSheet();
                receiveBottomSheet.setAddress(address); // 내 주소를 넘겨준다.
                receiveBottomSheet.show(getSupportFragmentManager(),"receive");
                break;
            case R.id.walletAddrLo:     // 지갑 주소 복사 버튼
                ClipboardManager clipboardManager = (ClipboardManager)getApplicationContext().getSystemService(CLIPBOARD_SERVICE);
                ClipData clipData = ClipData.newPlainText("label", address);
                if (clipboardManager != null) {
                    clipboardManager.setPrimaryClip(clipData);
                }
                Snackbar.make(walletReceiveBtn, "클립 보드에 복사되었습니다.",Snackbar.LENGTH_SHORT).show();
                break;
            case R.id.walletRefreshIv:
                walletRefreshIv.startAnimation(rotateAnim);
                getWalletInfo();
                break;
        }
    }

    private void setClickListeners(){
        walletMenuIv.setOnClickListener(this);
        walletRefillTv.setOnClickListener(this);
        walletNameEditIv.setOnClickListener(this);
        walletSendBtn.setOnClickListener(this);
        walletReceiveBtn.setOnClickListener(this);
        walletAddrLo.setOnClickListener(this);
        walletRefreshIv.setOnClickListener(this);
        walletNameConfirmIv.setOnClickListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(isInitWallet){
            getWalletInfo();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(requestCode == 3001){
            if(resultCode == Activity.RESULT_OK){
                Log.d("Wallet add result","OK");
                requestToken(data.getIntExtra("value",0));
            }else{
                Log.d("Wallet add result","NO");
            }
        }
        if(requestCode == 5000){
            Log.d("Scan result","request code 5000");
            IntentResult result = IntentIntegrator.parseActivityResult(resultCode,data);
            if(result.getContents() == null){
                Log.d("Scan result","content is null");
            }else{
                Log.d("Scan result","Scanned"+result.getContents());
                String targetAddress = result.getContents();    // QR 코드에서 읽어온 결과 값을 주소에 저장한다.
                runSendBs(targetAddress);   // 보내기 바텀시트를 시작한다.
            }
        }

    }

    /*
     * 보내기 바텀 시트를 실행하는 메소드
     *
     * QR CODE 스캔 후 주소를 추가하는 경우와 보내기 버튼을 클릭해서 실행하는 경우로 나뉜다.
     */
    private void runSendBs(String targetAddress){
        SendBottomSheet sendBottomSheet = SendBottomSheet.Companion.newInstance();
        sendBottomSheet.setCallback(new SendCallback() {
            @Override
            public void sendTo(@NotNull String address, int value) {
                Log.d("target",address);
                Log.d("value",".."+value);
                walletLoader.setVisibility(View.VISIBLE);
                walletSendBtn.setVisibility(View.INVISIBLE);
                Snackbar.make(walletReceiveBtn, "전송 요청하였습니다.",Snackbar.LENGTH_SHORT).show();
                sendTransaction(address, (long) value);
            }

            @Override
            public void scan() {
                IntentIntegrator integrator = new IntentIntegrator(WalletActivity.this);
                integrator.setDesiredBarcodeFormats(IntentIntegrator.ALL_CODE_TYPES);
                integrator.setCameraId(0);
                integrator.setBeepEnabled(false);
                integrator.setOrientationLocked(false);
                integrator.setPrompt("Scan");
                integrator.setRequestCode(5000);
                integrator.initiateScan();
            }
        });
        if(!"".equals(targetAddress)){
            sendBottomSheet.setAddress(targetAddress);
            @SuppressLint("CommitTransaction") FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
            ft.add(sendBottomSheet,"Send");
            ft.commitAllowingStateLoss();
        }else{
            sendBottomSheet.show(WalletActivity.this.getSupportFragmentManager(), "send");
        }
    }

    /*
     * 지갑을 초기화하는 메소드
     */
    @SuppressLint("CheckResult")
    private void initWallet(){
        if(web3j == null){
            web3j = Web3jService.getInstance();
        }
        try{
            Observable.create((ObservableOnSubscribe<KiwiTestToken>) emitter -> {

                KiwiTestToken kiwiToken = new KiwiTestToken(KiwiTestToken.CONTRACT_ADDRESS,
                        Web3jService.getInstance(),
                        KeyStoreUtils.getCredentials(address,getApplicationContext()),
                        BigInteger.valueOf(41),
                        BigInteger.valueOf(3000000));
                emitter.onNext(kiwiToken);
                emitter.onComplete();

            })
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(kiwiToken -> {
                        this.kiwiToken = kiwiToken;
                        Log.d("Contract","OK");
                        isInitWallet = true;    // 지갑 초기화가 완료 되면 true 를 저장한다.
                        getWalletInfo();    //  지갑 정보를 가져온다.
                        getTransactionLogs();     // 트랜잭션 리스트를 가져온다.
                    }, Throwable::printStackTrace);
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    /*
     * 지갑의 현재 토큰 개수를 확인한다.
     */
    @SuppressLint({"CheckResult", "SetTextI18n"})
    private void getWalletInfo() {
        Observable.create((ObservableOnSubscribe<BigInteger>) e -> {
            Log.d("tokenOwner",address+"..");
            //BigInteger send = kiwiToken.balanceOf("0x"+address).send();
            BigInteger send = kiwiToken.balanceOf(address).send();  // 이더리움 네트워크에 지갑 잔액을 가져오는 메소드를 호출함.
            e.onNext(send); // balanceOf 메소드 호출이 성공 한다면 send 를 파라미터로 넘겨준다.
            e.onComplete();
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(value -> {
                    //tvContent.setText(value.toString());
                    Log.d("value",value.toString()+"..");
                    walletAccountTv.setText(decimalFormat.format(value)+" KWT");
                    Snackbar.make(walletReceiveBtn, "지갑을 갱신하였습니다.",Snackbar.LENGTH_SHORT).show();
                    if(isFirstLoadValue){   // 처음 보여지는 로더를 안보이게한다.
                        walletRefreshLoader.setVisibility(View.GONE);
                        walletAccountTv.setVisibility(View.VISIBLE);
                        isFirstLoadValue = !isFirstLoadValue;
                    }
                }, Throwable::printStackTrace);
    }

    /*
     * 현재 지갑 주소와 연관된 트랜잭션 로그를 가져오는 메소드
     */
    private void getTransactionLogs(){
        new Thread(){
            @Override
            public void run() {
                transactionList.clear();
                EthFilter filter = new EthFilter(DefaultBlockParameterName.EARLIEST,
                        DefaultBlockParameterName.LATEST, KiwiTestToken.CONTRACT_ADDRESS);
                try {
                    List<EthLog.LogResult> list =  web3j.ethGetLogs(filter).send().getResult();
                    for(EthLog.LogResult logResult : list){
                        String fullLog = logResult.get().toString();
                        String logSplit[] = fullLog.split("',");
                        String hash = logSplit[2].replace(" transactionHash='","");
                        Long quantity = Long.parseLong(logSplit[6].replace("data='","").replace("0x","").replace(" ",""),16);

                        String topics[] = logSplit[8].replace("topics=[","").replace("]}","").split(", ");

                        String from = "0x"+topics[1].substring(26);
                        String to = "0x"+topics[2].substring(26);
                        if(address.equals(from)){   // 내가 보낸 트랜잭션, State = 0
                            transactionList.add(0,new wallet.kiwinam.charlie.kwtwallet.wallet.transaction.Transaction(
                                    hash,
                                    from,
                                    to,
                                    quantity,
                                    0
                            ));
                        }else if(KiwiTestToken.OWNER_ADDRESS.equals(from) && address.equals(to)){ // 내가 구매 요청한 트랜잭션 , State = 2
                            transactionList.add(0,new wallet.kiwinam.charlie.kwtwallet.wallet.transaction.Transaction(
                                    hash,
                                    from,
                                    to,
                                    quantity,
                                    2
                            ));
                        }else if(address.equals(to)){   // 내가 받은 트랜잭션 , State = 1
                            transactionList.add(0,new wallet.kiwinam.charlie.kwtwallet.wallet.transaction.Transaction(
                                    hash,
                                    from,
                                    to,
                                    quantity,
                                    1
                            ));
                        }
                    }
                    runOnUiThread(()->{
                            adapter.setTransactionList(transactionList);
                            walletSwipeLo.setRefreshing(false);
                            if(transactionList.size() > 0)
                                walletNoTransactionTv.setVisibility(View.GONE);
                            else
                                walletNoTransactionTv.setVisibility(View.VISIBLE);
                    });
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }.start();
    }

    /*
     * 전송 트랜잭션
     */
    @SuppressLint("CheckResult")
    private void sendTransaction(String to, Long value){
        Observable
                .create((ObservableOnSubscribe<TransactionReceipt>) e -> {
                    TransactionReceipt send  = kiwiToken.transfer(to,BigInteger.valueOf(value)).send();
                    e.onNext(send);
                    e.onComplete();
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(respons -> {
                    String result = respons.getBlockHash();
                    if (result != null) {
                        Log.e("transaction OK", result + "..");
                        walletLoader.setVisibility(View.GONE);
                        walletSendBtn.setVisibility(View.VISIBLE);
                        Snackbar.make(walletReceiveBtn, "토큰을 전송하였습니다.",Snackbar.LENGTH_SHORT).show();
                    } else {
                        //tvMgs.setText(respon.getError().getMessage() + "");
                        Log.e("transaction NO",  "..");
                    }
                }, Throwable::printStackTrace);
    }

    /*
     * 토큰 오너에게 토큰을 요청하는 메소드
     */
    @SuppressLint({"CheckResult", "SetTextI18n"})
    public void requestToken(int value){
        //mkLoader.setVisibility(View.VISIBLE);  // 프로그레스 바를 표시한다.
        walletAddTokenTv.setText("+"+decimalFormat.format(value));
        walletAddTokenTv.setVisibility(View.VISIBLE);
        walletValueLoader.setVisibility(View.VISIBLE);
        Log.d("request address",address);
        Log.d("request value",value+"..");
        Observable  // 토큰 요청 시작
                .create((ObservableOnSubscribe<TransactionReceipt>) e -> {
                    TransactionReceipt send  = kiwiToken.requestToken(address,BigInteger.valueOf(value)).send();
                    e.onNext(send);
                    e.onComplete();
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(respons -> {
                    String result = respons.getBlockHash();
                    if (result != null) {
                        Log.e("transaction OK", result + "..");
                        Toast.makeText(getApplicationContext(),"요청이 성공했습니다.",Toast.LENGTH_SHORT).show();
                        getWalletInfo();
                    } else {
                        Log.e("transaction NO",  "..");

                        Toast.makeText(getApplicationContext(),"요청이 실패했습니다.",Toast.LENGTH_SHORT).show();
                    }
                    walletAddTokenTv.setVisibility(View.GONE);
                    walletValueLoader.setVisibility(View.GONE);
                }, Throwable::printStackTrace);
        //mkLoader.setVisibility(View.GONE);  // 프로그레스 바를 숨긴다.
    }
}
