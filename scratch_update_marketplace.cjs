const fs = require('fs');

let content = fs.readFileSync('frontend/src/pages/MarketplaceView.tsx', 'utf8');

// 1. Update useCart destructuring and add syncCartWithCatalog useEffect
const oldUseCart = /const \{\s*cartItems,[\s\S]*?retryVerification,?\s*\} = useCart\(\);[\s\S]*?const featured = playbooks\.length > 0 \? playbooks\[featuredIndex % playbooks\.length\] : null;/;

const newUseCart = `const {
    cartItems,
    addToCart,
    removeFromCart,
    clearCart,
    isCartOpen,
    setIsCartOpen,
    syncCartWithCatalog,
  } = useCart();
  const featured = playbooks.length > 0 ? playbooks[featuredIndex % playbooks.length] : null;

  useEffect(() => {
    if (typeof syncCartWithCatalog === 'function') {
      if (errorMessage) {
        syncCartWithCatalog(playbooks, { isNetworkError: true });
      } else if (playbooks && playbooks.length > 0) {
        syncCartWithCatalog(playbooks, { isBackendVerified: true });
      }
    }
  }, [playbooks, errorMessage, syncCartWithCatalog]);`;

if (!oldUseCart.test(content)) {
  console.error('Could not find old useCart');
  process.exit(1);
}
content = content.replace(oldUseCart, newUseCart);

// 2. Update CartDrawer and CheckoutModal in JSX
const oldCartDrawer = /<CartDrawer[\s\S]*?onProceedToCheckout=\{[\s\S]*?\}\s*\/>/;
const newCartDrawer = `<CartDrawer
        isOpen={isCartOpen}
        onClose={() => setIsCartOpen(false)}
        cartItems={cartItems}
        onRemoveItem={removeFromCart}
        onClearCart={clearCart}
        onProceedToCheckout={(items) => {
          setIsCartOpen(false);
          const purchasable = items.filter(isPlaybookPurchasable);
          if (purchasable.length > 0) {
            setSelectedForCheckout(purchasable);
          }
        }}
      />`;

if (!oldCartDrawer.test(content)) {
  console.error('Could not find old CartDrawer');
  process.exit(1);
}
content = content.replace(oldCartDrawer, newCartDrawer);

const oldCheckoutModal = /<CheckoutModal[\s\S]*?items=\{selectedForCheckout\}\s*\/>/;
const newCheckoutModal = `<CheckoutModal
        isOpen={selectedForCheckout.length > 0}
        onClose={() => setSelectedForCheckout([])}
        items={selectedForCheckout}
        playbook={selectedForCheckout[0] || null}
      />`;

if (!oldCheckoutModal.test(content)) {
  console.error('Could not find old CheckoutModal');
  process.exit(1);
}
content = content.replace(oldCheckoutModal, newCheckoutModal);

fs.writeFileSync('frontend/src/pages/MarketplaceView.tsx', content, 'utf8');
console.log('Successfully updated MarketplaceView.tsx');

