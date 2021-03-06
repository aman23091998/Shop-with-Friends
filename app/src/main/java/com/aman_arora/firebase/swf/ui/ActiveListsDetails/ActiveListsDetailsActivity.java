package com.aman_arora.firebase.swf.ui.ActiveListsDetails;

import android.content.Intent;
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
import com.aman_arora.firebase.swf.ui.sharing.ShareListActivity;
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
    private ValueEventListener sharedWithValueEventListener;
    private DatabaseReference userListRef, currentUserRef, sharedWithRef;
    private ListItemAdapter listItemAdapter;
    private final String TAG = "ActiveListDetails";
    private Button mButtonShopping;
    private boolean isShopping;
    private User currentUser;
    private TextView shoppingUsersTV;
    private String listOwner;
    private boolean mCurrentUserIsOwner = false;
    private HashMap<String, User> sharedUsers;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_active_lists_details);

        initialise();

        invalidateOptionsMenu();

        sharedUsers = new HashMap<>();

        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                addListItem();
            }
        });
        mPushId = getIntent().getStringExtra(Constants.KEY_PUSH_ID_USER_LIST);
        userListRef = FirebaseDatabase.getInstance()
                .getReferenceFromUrl(Constants.FIREBASE_USER_LISTS_URL)
                .child(mEncodedEmail)
                .child(mPushId);
        currentListValueEventListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                shoppingList = dataSnapshot.getValue(ShoppingList.class);

                if (shoppingList == null) {
                    finish();
                    return;
                }

                setTitle(shoppingList.getListName());
                listOwner = shoppingList.getOwner();
                listItemAdapter.setListOwner(listOwner);
                mCurrentUserIsOwner = Utils.checkIfOwner(listOwner, mEncodedEmail);
                invalidateOptionsMenu();

                HashMap<String, User> userShopping = shoppingList.getShoppingUsers();
                if (userShopping != null && userShopping.size() > 0 && userShopping.containsKey(mEncodedEmail))
                    startShopping();
                else stopShopping();
                setWhosShopping(userShopping);
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.e(TAG, ActiveListsDetailsActivity.this.getString(R.string.log_error_the_read_failed)
                        + databaseError.getMessage());
            }
        };
        userListRef.addValueEventListener(currentListValueEventListener);

        currentUserRef = FirebaseDatabase.getInstance().getReferenceFromUrl(Constants.FIREBASE_USERS_URL).child(mEncodedEmail);
        currentUserValueEventListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                currentUser = dataSnapshot.getValue(User.class);
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.e(TAG, ActiveListsDetailsActivity.this.getString(R.string.log_error_the_read_failed)
                        + databaseError.getMessage());
            }
        };
        currentUserRef.addValueEventListener(currentUserValueEventListener);

        sharedWithRef = FirebaseDatabase.getInstance().getReferenceFromUrl(Constants.FIREBASE_SHARED_WITH_URL)
                .child(mPushId);

        sharedWithValueEventListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                for (DataSnapshot sharedUser : dataSnapshot.getChildren()) {
                    sharedUsers.put(sharedUser.getKey(), sharedUser.getValue(User.class));
                }
                listItemAdapter.setSharedWithUsersList(sharedUsers);
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.e(TAG, ActiveListsDetailsActivity.this.getString(R.string.log_error_the_read_failed)
                        + databaseError.getMessage());
            }
        };

        sharedWithRef.addValueEventListener(sharedWithValueEventListener);

        DatabaseReference listItemsReference = FirebaseDatabase.getInstance()
                .getReferenceFromUrl(Constants.FIREBASE_ITEM_URL).child(mPushId);

        listItemAdapter = new ListItemAdapter(this, ListItem.class, R.layout.single_active_list_item,
                listItemsReference.orderByChild(Constants.PROPERTY_ITEM_BOUGHT),
                mPushId, mEncodedEmail);
        mListDetails.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> adapterView, View view, int i, long l) {
                ListItem listItem = listItemAdapter.getItem(i);

                if (mEncodedEmail.equals(listItem.getOwner())) {

                    EditListItemDialog editListItemDialog = (EditListItemDialog) EditListItemDialog
                            .newInstance(listItemAdapter.getItem(i).getItemName(), mPushId,
                                    listItemAdapter.getRef(i).getKey(), shoppingList, mEncodedEmail, sharedUsers);
                    editListItemDialog.show(getFragmentManager(), "EditListItem");
                }
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

        AddListItemDialog dialog = AddListItemDialog.newInstance(mPushId, mEncodedEmail, shoppingList, sharedUsers);
        dialog.show(getFragmentManager(), "AddListItem");

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        getMenuInflater().inflate(R.menu.menu_list_items, menu);

        MenuItem remove = menu.findItem(R.id.action_remove_list);
        MenuItem edit = menu.findItem(R.id.action_edit_list_name);
        MenuItem share = menu.findItem(R.id.action_share_list);
        MenuItem archive = menu.findItem(R.id.action_archive);

        remove.setVisible(mCurrentUserIsOwner);
        edit.setVisible(mCurrentUserIsOwner);
        share.setVisible(mCurrentUserIsOwner);
        archive.setVisible(false);

        return true;

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int menuItemId = item.getItemId();

        switch (menuItemId) {
            case R.id.action_edit_list_name:
                showEditListDialog();
                return true;
            case R.id.action_remove_list:
                deleteListDialogs();
                return true;
            case R.id.action_share_list:
                if (currentUser.getUserVerified()) {
                    Intent intent = new Intent(this, ShareListActivity.class);
                    intent.putExtra(Constants.KEY_PUSH_ID_USER_LIST, mPushId);
                    startActivity(intent);
                } else showErrorToast(getString(R.string.user_email_not_verified));
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void deleteListDialogs() {
        RemoveListDialogFragment removeList = RemoveListDialogFragment.newInstance(mPushId, shoppingList, sharedUsers);
        removeList.show(getSupportFragmentManager(), "RemoveListName");

    }

    private void showEditListDialog() {
        EditListNameDialogFragment editListName = (EditListNameDialogFragment)
                EditListNameDialogFragment.newInstance(mPushId, shoppingList.getListName(),
                        shoppingList, mEncodedEmail, sharedUsers);
        editListName.show(getFragmentManager(), "EditListName");

    }

    @Override
    public void onDestroy() {
        if (currentListValueEventListener != null)
            userListRef.removeEventListener(currentListValueEventListener);
        if (currentUserValueEventListener != null)
            currentUserRef.removeEventListener(currentUserValueEventListener);
        if (sharedWithValueEventListener != null)
            sharedWithRef.removeEventListener(sharedWithValueEventListener);
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
        HashMap<String, Object> updatePackage = new HashMap<>();
        String updateKey = Constants.PROPERTY_LIST_SHOPPING_USERS + '/' + mEncodedEmail;

        if (!isShopping) {
            Utils.createUpdatePackage(updatePackage, shoppingList.getOwner(), mPushId, updateKey, currentUser, sharedUsers);
            for(Object object: updatePackage.entrySet()) Log.d(TAG, "toggleShopping: " + object.toString());
            Utils.createTimeStampUpdatePackage(updatePackage, shoppingList.getOwner(), mPushId, sharedUsers);
            firebaseRef.updateChildren(updatePackage);
            startShopping();

        } else {
            Utils.createUpdatePackage(updatePackage, shoppingList.getOwner(), mPushId, updateKey, null, sharedUsers);
            Utils.createTimeStampUpdatePackage(updatePackage, shoppingList.getOwner(), mPushId, sharedUsers);
            firebaseRef.updateChildren(updatePackage);
            stopShopping();
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

        if (shoppingUsers == null || shoppingUsers.size() <= 0) {
            shoppingUsersTV.setText("");
            return;
        }
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
                    text = getString(R.string.text_you_and_other_are_shopping, otherUsers.get(0));
                    break;
                default:
                    text = getString(R.string.text_you_and_number_are_shopping, otherUsers.size());
                    break;

            }
        } else {
            switch (otherUsers.size()) {
                case 1:
                    text = getString(R.string.text_other_is_shopping, otherUsers.get(0));
                    break;
                case 2:
                    text = getString(R.string.text_other_and_other_are_shopping
                            , otherUsers.get(0)
                            , otherUsers.get(1));
                    break;
                default:
                    text = getString(R.string.text_other_and_number_are_shopping
                            , otherUsers.get(0)
                            , otherUsers.size() - 1);
                    break;
            }
        }

        shoppingUsersTV.setText(text);
    }
}
