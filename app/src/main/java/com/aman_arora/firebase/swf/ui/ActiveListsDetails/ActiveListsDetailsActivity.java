package com.aman_arora.firebase.swf.ui.ActiveListsDetails;

import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import com.aman_arora.firebase.swf.R;
import com.aman_arora.firebase.swf.model.ListItem;
import com.aman_arora.firebase.swf.model.ShoppingList;
import com.aman_arora.firebase.swf.model.User;
import com.aman_arora.firebase.swf.ui.BaseActivity;
import com.aman_arora.firebase.swf.utils.Constants;
import com.aman_arora.firebase.swf.utils.Utils;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;

public class ActiveListsDetailsActivity extends BaseActivity {

    private ListView mListDetails;
    private FloatingActionButton fab;
    private ShoppingList shoppingList;
    private String mPushId;
    private ValueEventListener currentListValueEventListener, currentUserValueEventListener;
    private DatabaseReference activeListRef, currentUserRef;
    private ListItemAdapter listItemAdapter;
    private final String TAG = "ActiveListDetails";
    private Button mButtonShopping;
    private boolean isShopping;
    private User currentUser;
    private TextView shoppingUsersTV;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_active_lists_details);

        initialise();

        invalidateOptionsMenu();

        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                addListItem();
            }
        });
        mPushId = getIntent().getStringExtra(Constants.KEY_PUSH_ID_ACTIVE_LIST);
        activeListRef = FirebaseDatabase.getInstance().getReferenceFromUrl(Constants.FIREBASE_ACTIVE_LISTS_URL).child(mPushId);
        currentListValueEventListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                shoppingList = dataSnapshot.getValue(ShoppingList.class);

                if (shoppingList == null) {
                    finish();
                    return;
                }

                setTitle(shoppingList.getListName());
                HashMap<String, User> userShopping = shoppingList.getShoppingUsers();
                if (userShopping != null && userShopping.size() > 0 && userShopping.containsKey(mEncodedEmail))
                    startShopping();
                else stopShopping();
                setWhosShopping(userShopping);
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
            }
        };
        activeListRef.addValueEventListener(currentListValueEventListener);

        currentUserRef = FirebaseDatabase.getInstance().getReferenceFromUrl(Constants.FIREBASE_USER_LIST_URL).child(mEncodedEmail);
        currentUserValueEventListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                currentUser = dataSnapshot.getValue(User.class);
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.d(TAG, "currentUser ValueListener " + databaseError.getMessage());
            }
        };
        currentUserRef.addValueEventListener(currentUserValueEventListener);

        DatabaseReference listItemsReference = FirebaseDatabase.getInstance().getReferenceFromUrl(Constants.FIREBASE_ITEM_URL).child(mPushId);

        listItemAdapter = new ListItemAdapter(this, ListItem.class, R.layout.single_active_list_item, listItemsReference, mPushId, mEncodedEmail);
        mListDetails.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> adapterView, View view, int i, long l) {

                EditListItemDialog editListItemDialog = (EditListItemDialog) EditListItemDialog.newInstance(listItemAdapter.getItem(i).getItemName(), mPushId, listItemAdapter.getRef(i).getKey());
                editListItemDialog.show(getFragmentManager(), "EditListItem");

                return true;
            }
        });
        mListDetails.setAdapter(listItemAdapter);

        mListDetails.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                ListItem listItem = listItemAdapter.getItem(position);
                String listPushID = listItemAdapter.getRef(position).getKey();

                if (listItem != null) {
                    HashMap<String, Object> updatedItem = new HashMap<String, Object>();
                    if (isShopping) {
                        if (!listItem.isBought()) {
                            updatedItem.put(Constants.PROPERTY_ITEM_BOUGHT, true);
                            updatedItem.put(Constants.PROPERTY_ITEM_BOUGHT_BY, mEncodedEmail);
                        } else if (listItem.getBoughtBy().equals(mEncodedEmail)) {
                            updatedItem.put(Constants.PROPERTY_ITEM_BOUGHT, false);
                            updatedItem.put(Constants.PROPERTY_ITEM_BOUGHT_BY, null);
                        }

                        DatabaseReference itemRef = FirebaseDatabase.getInstance()
                                .getReferenceFromUrl(Constants.FIREBASE_ITEM_URL).child(mPushId).child(listPushID);
                        itemRef.updateChildren(updatedItem, new DatabaseReference.CompletionListener() {
                            @Override
                            public void onComplete(DatabaseError databaseError, DatabaseReference databaseReference) {
                                if (databaseError != null)
                                    Log.e(TAG, "onComplete: update click listItem " + databaseError.getMessage());
                            }
                        });
                    }
                }
            }
        });

    }

    private void addListItem() {

        AddListItemDialog dialog = AddListItemDialog.newInstance(mPushId, mEncodedEmail);
        dialog.show(getFragmentManager(), "AddListItem");

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        String owner = shoppingList.getOwner();
        if (Utils.encodeEmail(owner).equals(mEncodedEmail)) {
            getMenuInflater().inflate(R.menu.menu_list_items, menu);
        }

        return true;

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int menuItemId = item.getItemId();


        switch (menuItemId) {
            case R.id.action_edit_list_name:
                showEditListDialog();
                break;
            case R.id.action_remove_list:
                deleteListDialogs();
                break;

        }

        return super.onOptionsItemSelected(item);
    }

    private void deleteListDialogs() {
        RemoveListDialogFragment removeList = RemoveListDialogFragment.newInstance(mPushId);
        removeList.show(getSupportFragmentManager(), "RemoveListName");

    }

    private void showEditListDialog() {
        if (shoppingList == null) Log.d("124", "showEditListDialog:123312 ");
        EditListNameDialogFragment editListName = (EditListNameDialogFragment) EditListNameDialogFragment.newInstance(mPushId, shoppingList.getListName(), shoppingList);
        editListName.show(getFragmentManager(), "EditListName");

    }

    @Override
    public void onDestroy() {
        if (currentListValueEventListener != null)
            activeListRef.removeEventListener(currentListValueEventListener);
        if (currentUserValueEventListener != null)
            currentUserRef.removeEventListener(currentUserValueEventListener);
        listItemAdapter.cleanup();
        super.onDestroy();
    }

    private void initialise() {
//        Set the toolbar
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbarActiveListDetails);
        setSupportActionBar(toolbar);
        assert getSupportActionBar() != null;
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

//        Add list item
        fab = (FloatingActionButton) findViewById(R.id.fab_add_list_item);

//        list items
        mListDetails = (ListView) findViewById(R.id.list_view_active_lists_details);

        //shopping Users
        shoppingUsersTV = (TextView) findViewById(R.id.text_view_people_shopping);
        mButtonShopping = (Button) findViewById(R.id.button_shopping);
    }

    public void toggleShopping(View view) {
        DatabaseReference shoppingUsersRef = activeListRef.child(Constants.PROPERTY_LIST_SHOPPING_USERS)
                .child(mEncodedEmail);
        if (!isShopping) {
            startShopping();
            shoppingUsersRef.setValue(currentUser);
        } else {
            stopShopping();
            shoppingUsersRef.removeValue();

        }
    }

    private void stopShopping() {
        mButtonShopping.setBackgroundColor(ContextCompat.getColor(this, R.color.primary_dark));
        mButtonShopping.setText(getString(R.string.button_start_shopping));
        isShopping = false;
    }

    private void startShopping() {
        isShopping = true;
        mButtonShopping.setBackgroundColor(ContextCompat.getColor(this, R.color.dark_grey));
        mButtonShopping.setText(getString(R.string.button_stop_shopping));

    }

    public void setWhosShopping(HashMap<String, User> shoppingUsers) {

        if (shoppingUsers == null || shoppingUsers.size() <= 0 ) return;
        String text;

        ArrayList<String> otherUsers = new ArrayList<>();
        for (User users : shoppingUsers.values())
            if (users != null && !users.getEmail().equals(mEncodedEmail))
                otherUsers.add(users.getName());

        if (isShopping) {
            switch (otherUsers.size()) {
                case 0:
                    text = getString(R.string.text_you_are_shopping);
                    break;
                case 1:
                    text = getString(R.string.text_you_and_other_are_shopping)
                            .replace("%s", otherUsers.get(0));
                    break;
                default:
                    text = getString(R.string.text_you_and_number_are_shopping)
                            .replace("%d", String.valueOf(otherUsers.size()));
                    break;

            }
        }else{
            switch (otherUsers.size()){
                case 1:
                    text = getString(R.string.text_other_is_shopping)
                            .replace("%s", otherUsers.get(0));
                    break;
                case 2:
                    text = getString(R.string.text_other_and_other_are_shopping)
                            .replace("%1$s", otherUsers.get(0))
                            .replace("%2$s", otherUsers.get(1));
                    break;
                default:
                    text = getString(R.string.text_other_and_number_are_shopping)
                            .replace("%1$s", otherUsers.get(0))
                            .replace("%2$d", String.valueOf(otherUsers.size() -1));
                    break;
            }
        }

        shoppingUsersTV.setText(text);
    }
}
