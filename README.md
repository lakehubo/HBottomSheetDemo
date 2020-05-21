# HBottomSheetBehavior
自定义上拉dialog，由于Android原生自带的BottomSheetBehavior有冗余的中间折叠状态与半展状态
当需要实现一种简单的上拉框时，只需展开和隐藏状态，且当view低于某个窗口高度比例时才触发隐藏，否则回弹到全展开状态时，利用原生控件难以达到简单的需求
所以，我复制下了原生BottomSheetBehavior的代码，并进行了简单的裁剪，达到了上述的目的。
详细的使用可以参考demo
后续会逐渐更新优化
