# پیشرفت فورک 1code harness

اقدامات انجام‌شده تا این لحظه روی ریپوی فورک:

## زیرساخت و CI
1. ✅ فورک ریپو و کار روی `hamaz562006/Mobile-Harness`
2. ✅ تغییر `applicationId` برای نصب جدا از نسخهٔ اصلی
3. ✅ به‌روزرسانی `targetSdk` و نسخهٔ اپ
4. ✅ راه‌اندازی CI (بیلد Android + آپلود APK debug)
5. ✅ رفع خطاهای مکرر CI (از جمله NDK و چک حجم APK)
6. ✅ نسخهٔ Online بدون embed کردن Core داخل APK
7. ✅ نسخهٔ Online بدون embed کردن هیچ ایجنت داخل APK
8. ✅ دانلود Core و ایجنت‌ها در زمان setup (on demand)
9. ✅ جلوگیری در CI از بسته‌شدن فایل‌های `.tar.zst` رانتایم داخل APK Online

## پروایدر و مدل
10. ✅ افزودن Free models (Kilo / بدون API key) برای ایجنت‌ها
11. ✅ پیش‌فرض Free models در onboarding برای همهٔ ایجنت‌ها
12. ✅ پشتیبانی چند پروایدر و ذخیرهٔ تنظیمات هر کدام
13. ✅ بهبود وضعیت Online / Test connection بعد از تعویض پروایدر
14. ✅ جمع‌وجور کردن UI لیست مدل‌ها (حداکثر حدود دو خط)
15. ✅ نمایش جزئیات پروایدر/متا زیر هر مدل در لیست Discover (owned_by، Free، ctx، Tools/Thinking)

## تنظیمات و ظاهر
16. ✅ انتقال تنظیمات قابلیت اطمینان (اعلان / باتری / …) به Settings بعد از نصب
17. ✅ نمایش وضعیت واقعی مجوزها در Settings بعد از onboarding
18. ✅ بخش Appearance و انتخاب رنگ accent
19. ✅ رنگ پیش‌فرض بنفش برای نصب اول (از onboarding تا کل اپ)
20. ✅ اتصال رنگ‌های UI به `primary` تم (نه رنگ ثابت قدیمی)
21. ✅ گزینه‌های رنگ الهام‌گرفته از ایجنت‌ها (Claude / DeepSeek / Antigravity / نارنجی)

## زبان و بومی‌سازی
22. ✅ حذف زبان‌های اسپانیایی، چینی، عربی، هندی، روسی
23. ✅ افزودن زبان فارسی (Farsi) با پشتیبانی RTL
24. ✅ اعمال فونت وزیرمتن **فقط** برای زبان فارسی (`languageCode == fa`)
25. ✅ ترجمهٔ بخش‌های مهم رابط کاربری به فارسی
26. ✅ تغییر نام اپ انگلیسی به **1code harness**
27. ✅ تغییر نام اپ فارسی به **یک کد هارنس**

## 9Router (special provider)
28. ✅ یکپارچه‌سازی 9Router به‌صورت shared provider (نه در لیست onboarding تا قبل از link)
29. ✅ UI بخش Special providers در تب Agent (Install runtime → Install → Start/Stop → Add provider → Dashboard → Check updates)
30. ✅ مخفی بودن 9Router از لیست Connect your AI تا بعد از Add provider
31. ✅ دکمه‌های تک‌خطی (بدون wrap): Add provider / Dashboard / Check updates
32. ✅ احراز هویت CLI مطابق AndroidHarness + decolua/9router (`machine-id` UUID + `cli-secret` + توکن `x-9r-cli-token`)
33. ✅ seed فایل‌های auth و restart امن هنگام HTTP 401
34. ✅ ساخت خودکار integration API key در 9Router و ذخیره در vault اپ با نام نمایشی `1code-harness`
35. ✅ Base URL پروایدر: `http://127.0.0.1:20128/v1` (کلید محلی 9Router؛ نه کلید upstream)

## همگام‌سازی با upstream و آپدیت اپ
36. ✅ همگام‌سازی فورک با `techjarves/Mobile-Harness` تا v1.0.10 با حفظ تمام تغییرات فورک
37. ✅ دریافت قابلیت Backup & Restore و What's New از upstream
38. ✅ اصلاح `APP_UPDATE_MANIFEST_URL` (خالی) تا دیالوگ آپدیت خودکار از ریلیز techjarves نیاید — بررسی دوره‌ای دستی توسط مالک فورک
39. ✅ نسخه اپ `1.0.10` / versionCode `11` هم‌تراز با upstream (بدون تغییر applicationId فورک)

---

## یادداشت

- هدف: بستر تمیز + قابلیت‌های فورک (فارسی، 9Router، Free models) روی آخرین پایهٔ upstream.
- پروایدرهای بالادست 9Router (مثل OpenCode Free) فقط از داشبورد 9Router Connect می‌شوند؛ Add provider فقط لینک اپ↔9Router است.
- جزئیات بیشتر Agent / MCP در فازهای بعدی.
