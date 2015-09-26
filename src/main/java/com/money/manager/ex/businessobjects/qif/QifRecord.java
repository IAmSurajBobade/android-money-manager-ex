/*
 * Copyright (C) 2012-2015 The Android Money Manager Ex Project Team
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package com.money.manager.ex.businessobjects.qif;

import android.content.Context;
import android.text.TextUtils;

import com.money.manager.ex.Constants;
import com.money.manager.ex.core.Core;
import com.money.manager.ex.core.TransactionTypes;
import com.money.manager.ex.database.ISplitTransactionsDataset;
import com.money.manager.ex.database.QueryAllData;
import com.money.manager.ex.database.SplitCategoriesRepository;
import com.money.manager.ex.database.TransactionStatus;
import com.money.manager.ex.viewmodels.AccountTransaction;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

/**
 * A .qif file record. Represents a single transaction or account header record.
 * References:
 * QIF format
 * http://en.wikipedia.org/wiki/Quicken_Interchange_Format
 * Date formats inside qif file
 * http://www.unicode.org/reports/tr35/tr35-dates.html#Date_Format_Patterns
 */
public class QifRecord {
    public QifRecord(Context context) {
        mContext = context;
    }

    private Context mContext;

    /**
     * Parses the data and generates a QIF record for transaction.
     * @return A string representing one QIF record
     */
    public String parse(AccountTransaction transaction) throws ParseException {
        StringBuilder builder = new StringBuilder();

        // Date
        String date = parseDate(transaction);
        builder.append("D");
        builder.append(date);
        builder.append(System.lineSeparator());

        // Amount
        String amount = parseAmount(transaction);
//        builder.append("U");
//        builder.append(this.Amount);
//        builder.append(System.lineSeparator());
        builder.append("T");
        builder.append(amount);
        builder.append(System.lineSeparator());

        // Cleared status
//        String status = parseCleared(transaction);
        String status = transaction.getStatusCode();
        if (!TextUtils.isEmpty(status)) {
            // Cleared: * or c. We don't have Cleared in MMEX.
            // Reconciled: X or R
            builder.append("C");
            switch (status) {
                case "R":
                    builder.append(status);
                    break;
            }
        }

        // Payee
//        String payee = cursor.getString(cursor.getColumnIndex(QueryAllData.Payee));
        String payee = transaction.getPayee();
        if (!TextUtils.isEmpty(payee)) {
            builder.append("P");
            builder.append(payee);
            builder.append(System.lineSeparator());
        }

        // Categories / Transfers
        String category;
//        String transactionTypeName = getTransactionTypeName(transaction);
        TransactionTypes transactionType = transaction.getTransactionType();
        if (transactionType.equals(TransactionTypes.Transfer)) {
            // Category is the destination account name.
//            category = cursor.getString(cursor.getColumnIndex(QueryAllData.ToAccountName));
//            category = cursor.getString(cursor.getColumnIndex(QueryAllData.AccountName));
            category = transaction.getAccountName();
            // in square brackets
            category = "[%]".replace("%", category);
        } else {
            // Category
            category = parseCategory(transaction);
        }
        if (category != null) {
            builder.append("L");
            builder.append(category);
            builder.append(System.lineSeparator());
        }

        // Split Categories
//        int splitCategory = cursor.getInt(cursor.getColumnIndex(QueryAllData.SPLITTED));
        boolean splitCategory = transaction.getIsSplit();
        if (splitCategory) {
            String splits = getSplitCategories(transaction);
            builder.append(splits);
        }

        // Memo
//        String memo = parseMemo(cursor);
        String memo = transaction.getNotes();
        if (!TextUtils.isEmpty(memo)) {
            builder.append("M");
            builder.append(memo);
            builder.append(System.lineSeparator());
        }

        builder.append("^");
        builder.append(System.lineSeparator());

        return builder.toString();
    }

//    public int getAccountId(AccountTransaction transaction) {
////        int accountId = cursor.getInt(cursor.getColumnIndex(QueryAllData.ACCOUNTID));
//        int accountId = cursor.getInt(cursor.getColumnIndex(QueryAllData.TOACCOUNTID));
//        return accountId;
//    }

    public String getSplitCategories(AccountTransaction transaction) {
        StringBuilder builder = new StringBuilder();

        // retrieve splits
        SplitCategoriesRepository repo = new SplitCategoriesRepository(mContext);
//        int transactionId = getTransactionId(cursor);
        int transactionId = transaction.getId();
        ArrayList<ISplitTransactionsDataset> splits = repo.loadSplitCategoriesFor(transactionId);
        if (splits == null) return Constants.EMPTY_STRING;

//        String transactionType = getTransactionTypeName(cursor);
        String transactionType = transaction.getTransactionTypeName();

        for(ISplitTransactionsDataset split : splits) {
            String splitRecord = getSplitCategory(split, transactionType);
            builder.append(splitRecord);
        }

        return builder.toString();
    }

    private String getSplitCategory(ISplitTransactionsDataset split, String transactionType) {
        StringBuilder builder = new StringBuilder();
        Core core = new Core(mContext);

        // S = category in split
        // $ = amount in split
        // E = memo in split

        // category
        String category = core.getCategSubName(split.getCategId(), split.getSubCategId());
        builder.append("S");
        builder.append(category);
        builder.append(System.lineSeparator());

        // amount
        double amount = split.getSplitTransAmount();
        // handle sign
        if (TransactionTypes.valueOf(transactionType).equals(TransactionTypes.Withdrawal)) {
            amount = amount * (-1);
        }
        if (TransactionTypes.valueOf(transactionType).equals(TransactionTypes.Deposit)) {
            // leave positive?
        }
        builder.append("$");
        builder.append(amount);
        builder.append(System.lineSeparator());

        // memo - currently we don't have a field for it.
//        String memo = split.get

        return builder.toString();
    }

//    private int getTransactionId(AccountTransaction transaction){
//        return cursor.getInt(cursor.getColumnIndex(QueryAllData.ID));
//    }

//    private String parseCleared(AccountTransaction transaction) {
//        return cursor.getString(cursor.getColumnIndex(QueryAllData.Status));
//    }

    private String parseDate(AccountTransaction transaction) throws ParseException {
        Date date = transaction.getDate();

        // todo: get Quicken date format from settings.
        SimpleDateFormat qifFormat = new SimpleDateFormat("MM/dd''yy", Locale.US);

        return qifFormat.format(date);
    }

    private String parseAmount(AccountTransaction transaction) {
//        Double amountDouble = cursor.getDouble(cursor.getColumnIndex(QueryAllData.Amount));
//        Double amountDouble = cursor.getDouble(cursor.getColumnIndex(QueryAllData.ToAmount));
//        String amount = Double.toString(amountDouble);
//        return amount;
        String amount;
        if (transaction.getTransactionType().equals(TransactionTypes.Transfer)) {
            amount = transaction.getToAmount().toString();
        } else {
            amount = transaction.getAmount().toString();
        }
        return amount;
    }

    private String parseCategory(AccountTransaction transaction) {
//        String category = cursor.getString(cursor.getColumnIndex(QueryAllData.Category));
        String category = transaction.getCategory();
//        String subCategory = cursor.getString(cursor.getColumnIndex(QueryAllData.Subcategory));
        String subCategory = transaction.getSubcategory();

        if (!TextUtils.isEmpty(subCategory)) {
            return category + ":" + subCategory;
        } else {
            return category;
        }
    }

//    private String parseMemo(AccountTransaction transaction) {
//        return cursor.getString(cursor.getColumnIndex(QueryAllData.Notes));
//    }

//    private String getTransactionTypeName(AccountTransaction transaction) {
//        String type = cursor.getString(cursor.getColumnIndex(QueryAllData.TransactionType));
//        return type;
//    }
}
